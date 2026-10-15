package dev.bec.hdpanel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Bounded data contract: no images, code or arbitrary drawing instructions cross the network. */
public final class PanelModel {
    private PanelModel() { }
    public static String text(Object value, int limit) {
        String s = value instanceof byte[] ? new String((byte[]) value, StandardCharsets.UTF_8)
            : value == null ? "" : String.valueOf(value);
        s = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        return s.length() <= limit ? s : s.substring(0, limit);
    }
    private static double number(Object value) {
        if (!(value instanceof Number)) return 0;
        double n = ((Number) value).doubleValue();
        return Double.isNaN(n) || Double.isInfinite(n) ? 0 : Math.max(0, Math.min(1E18, n));
    }
    public static String normalize(Map<?, ?> input) {
        JsonObject data = new JsonObject();
        for (String key : new String[] {"status", "stage", "recipe", "mode"})
            data.addProperty(key, text(input.get(key), 128));
        for (String key : new String[] {"parallel", "maxParallel", "naniteTier", "naniteCount", "requiredTier",
                "cachePercent", "progress", "progressMax", "uptime", "configTarget"})
            data.addProperty(key, number(input.get(key)));
        data.addProperty("controls", Boolean.TRUE.equals(input.get("controls")));
        data.addProperty("configEditable", Boolean.TRUE.equals(input.get("configEditable")));
        data.addProperty("bindingEditable", Boolean.TRUE.equals(input.get("bindingEditable")));
        data.add("bindings", BindingModel.normalize(input.get("bindings")));
        data.addProperty("bindingRevision",text(input.get("bindingRevision"),16));
        JsonArray fluids = new JsonArray();
        Object source = input.get("fluids");
        if (source instanceof Map) {
            Map<?, ?> rows = (Map<?, ?>) source;
            for (int i = 1; i <= 32; i++) {
                Object row = rows.get(Double.valueOf(i));
                if (row == null) row = rows.get(Integer.valueOf(i));
                if (!(row instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) row;
                JsonObject f = new JsonObject();
                f.addProperty("name", text(m.get("name"), 48));
                f.addProperty("amount", number(m.get("amount")));
                f.addProperty("target", number(m.get("target")));
                fluids.add(f);
            }
        }
        data.add("fluids", fluids);
        JsonArray logs = new JsonArray();
        source = input.get("logs");
        if (source instanceof Map) {
            Map<?, ?> rows = (Map<?, ?>) source;
            for (int i = 1; i <= 8; i++) {
                Object row = rows.get(Double.valueOf(i));
                if (row == null) row = rows.get(Integer.valueOf(i));
                if (row != null) logs.add(new com.google.gson.JsonPrimitive(text(row, 160)));
            }
        }
        data.add("logs", logs);
        return data.toString();
    }
}
