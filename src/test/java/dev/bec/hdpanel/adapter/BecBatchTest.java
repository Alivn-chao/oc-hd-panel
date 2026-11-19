package dev.bec.hdpanel.adapter;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BecBatchTest {
    private static class Fake implements BecBatch.Node {
        int min = 1, max = 256, writes;
        boolean idle = true, failWrite;

        @Override public Object identity() { return this; }
        @Override public void requireIdleClosed() { if (!idle) throw new IllegalStateException("busy"); }
        @Override public int min() { return min; }
        @Override public int max() { return max; }
        @Override public void limits(int min, int max) {
            writes++;
            this.min = min;
            if (failWrite) {
                failWrite = false;
                throw new IllegalStateException("write interrupted");
            }
            this.max = max;
        }
    }

    private static Map<String, Object> row(String address, int quota) {
        Map<String, Object> row = new HashMap<>();
        row.put("address", address.getBytes(StandardCharsets.UTF_8));
        row.put("min", (double) quota);
        row.put("max", (double) quota);
        return row;
    }

    @Test public void configuresAll64Slots() {
        Map<Double, Map<String, Object>> plan = new HashMap<>();
        Fake[] nodes = new Fake[64];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Fake();
            plan.put((double) i + 1, row("node-" + (i + 1), i + 1));
        }
        assertEquals("configured", BecBatch.configure(plan, 64, (slot, address) -> {
            assertEquals("node-" + slot, address);
            return nodes[slot - 1];
        }).get("status"));
        assertEquals(64, nodes[63].min);
        assertEquals(64, nodes[63].max);
    }

    @Test public void busyNodePreventsAllWrites() {
        Fake idle = new Fake(), busy = new Fake();
        busy.idle = false;
        assertEquals("rejected", BecBatch.configure(plan(), 64, (slot, address) -> slot == 1 ? idle : busy).get("status"));
        assertEquals(0, idle.writes);
        assertEquals(0, busy.writes);
    }

    @Test public void duplicatePhysicalNodePreventsAllWrites() {
        Fake node = new Fake();
        assertEquals("rejected", BecBatch.configure(plan(), 64, (slot, address) -> node).get("status"));
        assertEquals(0, node.writes);
    }

    @Test public void partialWriteRestoresBothTouchedNodes() {
        Fake first = new Fake(), second = new Fake();
        second.failWrite = true;
        assertEquals("restored", BecBatch.configure(plan(), 64, (slot, address) -> slot == 1 ? first : second).get("status"));
        for (Fake node : new Fake[] {first, second}) {
            assertEquals(1, node.min);
            assertEquals(256, node.max);
            assertEquals(2, node.writes);
        }
    }

    private static Map<Integer, Map<String, Object>> plan() {
        Map<Integer, Map<String, Object>> plan = new HashMap<>();
        plan.put(1, row("first", 2));
        plan.put(32, row("second", 4));
        return plan;
    }
}
