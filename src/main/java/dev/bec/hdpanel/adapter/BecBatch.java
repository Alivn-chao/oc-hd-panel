package dev.bec.hdpanel.adapter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class BecBatch {
    interface Node {
        Object identity();
        void requireIdleClosed() throws ReflectiveOperationException;
        int min() throws ReflectiveOperationException;
        int max() throws ReflectiveOperationException;
        void limits(int min, int max) throws ReflectiveOperationException;
    }

    interface Resolver {
        Node resolve(int slot, String address) throws ReflectiveOperationException;
    }

    private static final class Change {
        final Node node;
        final int beforeMin, beforeMax, min, max;
        boolean touched;

        Change(Node node, int min, int max) throws ReflectiveOperationException {
            this.node = node;
            this.min = min;
            this.max = max;
            beforeMin = node.min();
            beforeMax = node.max();
        }
    }

    static Map<String, Object> configure(Map<?, ?> plan, int capacity, Resolver resolver) {
        if (plan.isEmpty() || plan.size() > capacity) throw new IllegalArgumentException("invalid plan size");
        Map<Integer, Change> changes = new TreeMap<>();
        Map<Integer, Map<String, Object>> rows = new TreeMap<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<?, ?> entry : plan.entrySet()) {
            int slot = positiveInteger(entry.getKey());
            if (slot > capacity || !(entry.getValue() instanceof Map)) {
                throw new IllegalArgumentException("invalid slot or plan entry");
            }
            if (rows.containsKey(slot)) throw new IllegalArgumentException("duplicate slot");
            Map<?, ?> input = (Map<?, ?>) entry.getValue();
            String address = text(field(input, "address"));
            int min = positiveInteger(field(input, "min"));
            int max = positiveInteger(field(input, "max"));
            if (min > max) throw new IllegalArgumentException("minimum exceeds maximum");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("address", address);
            row.put("status", "checked");
            rows.put(slot, row);
            try {
                Node node = resolver.resolve(slot, address);
                if (!seen.add(node.identity())) throw new IllegalStateException("same physical BEC node appears twice");
                node.requireIdleClosed();
                Change change = new Change(node, min, max);
                if (change.beforeMin < 1 || change.beforeMax < change.beforeMin) {
                    throw new IllegalStateException("existing parallel limits are invalid");
                }
                changes.put(slot, change);
                row.put("beforeMin", change.beforeMin);
                row.put("beforeMax", change.beforeMax);
            } catch (Exception | LinkageError failure) {
                row.put("status", "rejected");
                row.put("error", BecNodeAccess.message(failure));
            }
        }
        if (changes.size() != rows.size()) return result("rejected", rows);

        try {
            for (Map.Entry<Integer, Change> entry : changes.entrySet()) {
                Change c = entry.getValue();
                c.touched = true;
                apply(c.node, c.min, c.max);
                Map<String, Object> row = rows.get(entry.getKey());
                row.put("min", c.min);
                row.put("max", c.max);
                row.put("status", "configured");
            }
            return result("configured", rows);
        } catch (Exception | LinkageError failure) {
            boolean restored = true;
            for (Map.Entry<Integer, Change> entry : changes.entrySet()) {
                Change c = entry.getValue();
                if (!c.touched) continue;
                Map<String, Object> row = rows.get(entry.getKey());
                try {
                    apply(c.node, c.beforeMin, c.beforeMax);
                    row.put("min", c.beforeMin);
                    row.put("max", c.beforeMax);
                    row.put("status", "restored");
                } catch (Exception | LinkageError rollback) {
                    restored = false;
                    row.remove("min");
                    row.remove("max");
                    row.put("status", "uncertain");
                    row.put("error", BecNodeAccess.message(rollback));
                }
            }
            Map<String, Object> result = result(restored ? "restored" : "uncertain", rows);
            result.put("error", BecNodeAccess.message(failure));
            return result;
        }
    }

    private static void apply(Node node, int min, int max) throws ReflectiveOperationException {
        node.limits(min, max);
        if (node.min() != min || node.max() != max) throw new IllegalStateException("parallel readback mismatch");
    }

    private static Map<String, Object> result(String status, Map<Integer, Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("nodes", rows);
        return result;
    }

    private static Object field(Map<?, ?> map, String name) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (name.equals(key) || key instanceof byte[] && name.equals(text(key))) return entry.getValue();
        }
        throw new IllegalArgumentException("missing " + name);
    }

    private static String text(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof byte[]) return new String((byte[]) value, StandardCharsets.UTF_8);
        throw new IllegalArgumentException("expected string");
    }

    private static int positiveInteger(Object value) {
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number >= 1 && number <= Integer.MAX_VALUE && number == Math.rint(number)) return (int) number;
        }
        throw new IllegalArgumentException("expected positive integer");
    }
}
