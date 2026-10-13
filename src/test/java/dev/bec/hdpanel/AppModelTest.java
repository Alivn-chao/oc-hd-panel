package dev.bec.hdpanel;
import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppModelTest {
    private Map<String,Object> widget(String id,String type,int x,int y,int width,int height) {
        Map<String,Object> w=new HashMap<String,Object>();w.put("id",id);w.put("type",type);w.put("x",x);w.put("y",y);w.put("w",width);w.put("h",height);w.put("label","自定义应用");return w;
    }
    private Map<String,Object> layout(Map<String,Object>... widgets) {
        Map<Double,Object> rows=new HashMap<Double,Object>();for(int i=0;i<widgets.length;i++)rows.put((double)i+1,widgets[i]);
        Map<String,Object> p=new HashMap<String,Object>();p.put("id","main");p.put("label","首页");p.put("widgets",rows);
        Map<Double,Object> pages=new HashMap<Double,Object>();pages.put(1.0,p);
        Map<String,Object> root=new HashMap<String,Object>();root.put("title","自动化控制台");root.put("pages",pages);root.put("javascript","bad code");return root;
    }
    @Test public void declarativeValuesAreBoundedAndDisabledUntilExplicitlyEnabled() {
        String l=AppModel.layout(layout(widget("run","button",40,124,200,60)));
        assertFalse(l.contains("javascript"));assertFalse(AppModel.actionable(l,"{}","run",0));
        Map<String,Object> value=new HashMap<String,Object>();value.put("enabled",true);value.put("value",Double.NaN);value.put("text",new String(new char[300]).replace('\0','a'));
        Map<String,Object> values=new HashMap<String,Object>();values.put("run",value);values.put("unknown",value);
        String v=AppModel.values(l,values);JsonObject parsed=AppModel.parse(v);
        assertFalse(parsed.has("unknown"));assertEquals(128,parsed.getAsJsonObject("run").get("text").getAsString().length());
        assertEquals(0,parsed.getAsJsonObject("run").get("value").getAsInt());assertTrue(AppModel.actionable(l,v,"run",0));assertFalse(AppModel.actionable(l,v,"run",1));
    }
    @Test public void rejectsOverlappingReservedAreaInvalidIdsAndDuplicateIds() {
        Map<String,Object>[] invalid=new Map[]{widget("<script>","button",40,124,200,60),widget("run","button",40,124,1240,60),widget("run","value",40,124,200,40)};
        for(Map<String,Object> w:invalid) {try{AppModel.layout(layout(w));fail("invalid widget accepted");}catch(IllegalArgumentException expected){}}
        try{AppModel.layout(layout(widget("run","button",40,124,200,60),widget("run","button",40,224,200,60)));fail("duplicate accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void hitTestMatchesWidgetAndToggleOnlyAcceptsZeroOrOne() {
        String l=AppModel.layout(layout(widget("switch","toggle",40,124,200,60)));
        assertNull(AppModel.buttonAt(l,0,240,140));assertEquals("switch",AppModel.buttonAt(l,0,100,140).get("id").getAsString());assertNull(AppModel.buttonAt(l,1,100,140));
        String v="{\"switch\":{\"enabled\":true}}";
        assertTrue(AppModel.actionable(l,v,"switch",0));assertTrue(AppModel.actionable(l,v,"switch",1));assertFalse(AppModel.actionable(l,v,"switch",2));assertFalse(AppModel.actionable(l,v,"other",0));
    }
}
