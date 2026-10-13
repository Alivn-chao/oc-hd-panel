package dev.bec.hdpanel;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelModelTest {
    @Test public void boundsUntrustedSnapshotAndRejectsNonFiniteNumbers() {
        Map<String, Object> input = new HashMap<String, Object>();
        input.put("status", new String(new char[300]).replace('\0', 'A'));
        input.put("parallel", Double.NaN); input.put("naniteCount", Double.POSITIVE_INFINITY);
        input.put("maxParallel", -12); input.put("arbitraryCode", "hello");
        JsonObject result = new JsonParser().parse(PanelModel.normalize(input)).getAsJsonObject();
        assertEquals(128, result.get("status").getAsString().length());
        assertEquals(0, result.get("parallel").getAsInt());
        assertEquals(0, result.get("naniteCount").getAsInt());
        assertEquals(0, result.get("maxParallel").getAsInt());
        assertFalse(result.has("arbitraryCode"));
        assertFalse(result.get("controls").getAsBoolean());
    }
    @Test public void boundsLuaRowsAndDecodesUtf8Values() throws Exception {
        Map<String, Object> input = new HashMap<String, Object>();
        Map<Double, Object> rows = new HashMap<Double, Object>();
        for (int i = 1; i <= 100; i++) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("name", "中文流体".getBytes("UTF-8")); row.put("amount", 1234.0); row.put("target", 5000.0);
            rows.put((double)i, row);
        }
        input.put("fluids", rows);
        JsonObject result = new JsonParser().parse(PanelModel.normalize(input)).getAsJsonObject();
        assertEquals(32, result.getAsJsonArray("fluids").size());
        assertEquals("中文流体", result.getAsJsonArray("fluids").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1234, result.getAsJsonArray("fluids").get(0).getAsJsonObject().get("amount").getAsInt());
    }
}
