package dev.bec.hdpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BindingModelTest {
    private static final String UUID="01234567-1234-4567-89ab-123456789abc";
    private JsonObject snapshot() {
        Map<String,Object> root=new HashMap<String,Object>();root.put("bindingEditable",true);root.put("bindingRevision","123");
        Map<Double,Object> fields=new HashMap<Double,Object>();
        Map<String,Object> field=new HashMap<String,Object>();
        field.put("key","n1.address");field.put("label","节点1");field.put("kind","uuid");field.put("value",UUID);
        fields.put(1.0,field);root.put("bindings",fields);
        return new com.google.gson.JsonParser().parse(PanelModel.normalize(root)).getAsJsonObject();
    }
    @Test public void acceptsPastedUuidAndKeepsLuaArrayMetadata() {
        JsonObject data=snapshot();assertEquals(1,data.getAsJsonArray("bindings").size());
        assertTrue(BindingModel.accepts(data.toString(),"_revision=123\nn1.address="+UUID));
        assertFalse(BindingModel.accepts(data.toString(),"_revision=123\nn1.address=bad"));
    }
    @Test public void rejectsStaleRevisionUnknownKeysDuplicatesAndLockedPage() {
        JsonObject data=snapshot();
        assertFalse(BindingModel.accepts(data.toString(),"_revision=122\nn1.address="+UUID));
        assertFalse(BindingModel.accepts(data.toString(),"_revision=123\nunknown="+UUID));
        assertFalse(BindingModel.accepts(data.toString(),"_revision=123\nn1.address="+UUID+"\nn1.address="+UUID));
        data.addProperty("bindingEditable",false);
        assertFalse(BindingModel.accepts(data.toString(),"_revision=123\nn1.address="+UUID));
    }
    @Test public void rejectsCommandsAndInvalidDirections() {
        assertFalse(BindingModel.valid("uuid","os.execute('bad')"));
        assertFalse(BindingModel.valid("path","/home/../bad"));
        assertFalse(BindingModel.valid("side","6"));
        assertFalse(BindingModel.valid("integer","2147483648"));
        assertTrue(BindingModel.valid("side","0"));
        assertTrue(BindingModel.valid("mode","cell-io"));
        assertFalse(BindingModel.valid("unknown",""));
    }
    @Test public void oversizedPayloadIsRejectedBeforeParsing() {
        assertFalse(BindingModel.accepts(snapshot().toString(),new String(new char[BindingModel.MAX_PAYLOAD+1])));
    }
    @Test public void incompleteDraftRequiresSeparatePermissionAndCannotBeApplied() {
        JsonObject data=snapshot();String partial="_revision=123\nn1.address=01234";
        assertFalse(BindingModel.accepts(data.toString(),partial,true));
        data.addProperty("bindingDraftEditable",true);
        assertTrue(BindingModel.accepts(data.toString(),partial,true));
        assertFalse(BindingModel.accepts(data.toString(),partial));
        assertFalse(BindingModel.accepts(data.toString(),"_revision=122\nn1.address=01234",true));
        assertFalse(BindingModel.accepts(data.toString(),partial+"\nunknown=x",true));
    }
    @Test public void draftMetadataAllowsPartialTextButRejectsStructureInjection() {
        assertTrue(BindingModel.draftValue("uuid","0123"));
        assertTrue(BindingModel.draftValue("side",""));
        assertFalse(BindingModel.draftValue("uuid","x\nunknown=y"));
        assertFalse(BindingModel.draftValue("unknown",""));
    }
}
