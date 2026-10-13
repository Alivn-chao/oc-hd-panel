package dev.bec.hdpanel;

import com.google.gson.*;
import java.util.*;

/** Declarative, bounded application contract; no executable HTML or JavaScript. */
public final class AppModel {
    public static final String WELCOME="{\"title\":\"OC 高清屏幕\",\"pages\":[]}";
    private AppModel() { }
    private static Object row(Map<?,?> m,int i) { Object r=m.get((double)i);return r==null?m.get(i):r; }
    private static int integer(Object n,int min,int max) {
        if(!(n instanceof Number)) throw new IllegalArgumentException("widget geometry must be numeric");
        double v=((Number)n).doubleValue();
        if(!Double.isFinite(v) || v!=Math.floor(v) || v<min || v>max) throw new IllegalArgumentException("widget geometry out of bounds");
        return (int)v;
    }
    private static String id(Object v) {
        String s=PanelModel.text(v,33);
        if(!s.matches("[A-Za-z0-9_.-]{1,32}")) throw new IllegalArgumentException("id must be 1-32 ASCII letters, digits, ._- ");return s;
    }
    public static String layout(Map<?,?> input) {
        JsonObject out=new JsonObject();out.addProperty("title",PanelModel.text(input.get("title"),48));
        out.addProperty("subtitle",PanelModel.text(input.get("subtitle"),80));
        if(!(input.get("pages") instanceof Map)) throw new IllegalArgumentException("pages table required");
        Map<?,?> pages=(Map<?,?>)input.get("pages");JsonArray normalized=new JsonArray();Set<String> ids=new HashSet<String>(),pageIds=new HashSet<String>();int count=0;
        if(pages.size()>4) throw new IllegalArgumentException("at most 4 pages");
        for(int p=1;p<=4;p++) {
            Object raw=row(pages,p);if(raw==null) continue;
            if(!(raw instanceof Map)) throw new IllegalArgumentException("page must be table");
            Map<?,?> page=(Map<?,?>)raw;JsonObject view=new JsonObject();
            String pageId=id(page.get("id"));if(!pageIds.add(pageId))throw new IllegalArgumentException("duplicate page id");
            view.addProperty("id",pageId);view.addProperty("label",PanelModel.text(page.get("label"),16));
            if(!(page.get("widgets") instanceof Map)) throw new IllegalArgumentException("widgets table required");
            Map<?,?> widgets=(Map<?,?>)page.get("widgets");if(widgets.size()>32) throw new IllegalArgumentException("at most 32 widgets total");
            JsonArray list=new JsonArray();
            for(int i=1;i<=32;i++) {
                Object source=row(widgets,i);if(source==null) continue;
                if(!(source instanceof Map) || ++count>32) throw new IllegalArgumentException("at most 32 widgets total");
                Map<?,?> w=(Map<?,?>)source;JsonObject widget=new JsonObject();String key=id(w.get("id"));
                if(!ids.add(key)) throw new IllegalArgumentException("duplicate widget id");
                String type=PanelModel.text(w.get("type"),16);
                if(!Arrays.asList("text","value","progress","button","toggle").contains(type)) throw new IllegalArgumentException("unsupported widget type");
                int x=integer(w.get("x"),20,1220),y=integer(w.get("y"),96,580),width=integer(w.get("w"),60,1240),height=integer(w.get("h"),40,524);
                if(x+width>1260 || y+height>620) throw new IllegalArgumentException("widgets must fit content area x20..1260 y96..620");
                if((type.equals("value")&&height<90)||(type.equals("progress")&&height<110))throw new IllegalArgumentException("value/progress cards need height 90/110 or greater");
                widget.addProperty("id",key);widget.addProperty("type",type);
                widget.addProperty("x",x);widget.addProperty("y",y);widget.addProperty("w",width);widget.addProperty("h",height);
                for(String field:new String[]{"label","unit","confirm"}) widget.addProperty(field,PanelModel.text(w.get(field),field.equals("confirm")?128:48));
                list.add(widget);
            }
            view.add("widgets",list);normalized.add(view);
        }
        if(normalized.size()==0) throw new IllegalArgumentException("at least one page required");
        out.add("pages",normalized);return out.toString();
    }
    private static double number(Object value) {
        if(!(value instanceof Number)) return 0;double n=((Number)value).doubleValue();
        return Double.isFinite(n)?Math.max(0,Math.min(1E18,n)):0;
    }
    public static String values(String layout,Map<?,?> input) {
        JsonObject out=new JsonObject();JsonArray pages=parse(layout).getAsJsonArray("pages");
        for(JsonElement page:pages) for(JsonElement element:page.getAsJsonObject().getAsJsonArray("widgets")) {
            String id=element.getAsJsonObject().get("id").getAsString();Object raw=input.get(id);
            if(!(raw instanceof Map)) continue;Map<?,?> source=(Map<?,?>)raw;JsonObject value=new JsonObject();
            value.addProperty("text",PanelModel.text(source.get("text"),128));
            value.addProperty("value",number(source.get("value")));value.addProperty("max",number(source.get("max")));
            value.addProperty("enabled",Boolean.TRUE.equals(source.get("enabled")));value.addProperty("active",Boolean.TRUE.equals(source.get("active")));
            out.add(id,value);
        } return out.toString();
    }
    public static JsonObject parse(String s) { try{return new JsonParser().parse(s).getAsJsonObject();}catch(RuntimeException e){return new JsonObject();} }
    public static JsonArray widgets(String layout,int page) {
        JsonObject o=parse(layout);if(!o.has("pages"))return new JsonArray();JsonArray a=o.getAsJsonArray("pages");
        return page<0 || page>=a.size()?new JsonArray():a.get(page).getAsJsonObject().getAsJsonArray("widgets");
    }
    public static JsonObject value(String values,String id) { JsonObject v=parse(values);return v.has(id)&&v.get(id).isJsonObject()?v.getAsJsonObject(id):new JsonObject(); }
    public static boolean enabled(String values,String id) { JsonObject v=value(values,id);return v.has("enabled")&&v.get("enabled").getAsBoolean(); }
    public static JsonObject buttonAt(String layout,int page,int x,int y) {
        JsonArray list=widgets(layout,page);
        for(int i=list.size()-1;i>=0;i--) {
            JsonObject w=list.get(i).getAsJsonObject();String type=w.get("type").getAsString();
            if(!(type.equals("button")||type.equals("toggle")))continue;
            int l=w.get("x").getAsInt(),t=w.get("y").getAsInt();
            if(x>=l&&x<l+w.get("w").getAsInt()&&y>=t&&y<t+w.get("h").getAsInt())return w;
        }return null;
    }
    public static boolean actionable(String layout,String values,String id,int next) {
        JsonObject root=parse(layout);if(!root.has("pages"))return false;
        for(JsonElement page:root.getAsJsonArray("pages"))for(JsonElement element:page.getAsJsonObject().getAsJsonArray("widgets")) {
            JsonObject w=element.getAsJsonObject();if(!w.get("id").getAsString().equals(id))continue;
            String type=w.get("type").getAsString();return enabled(values,id)&&((type.equals("button")&&next==0)||(type.equals("toggle")&&(next==0||next==1)));
        }return false;
    }
}
