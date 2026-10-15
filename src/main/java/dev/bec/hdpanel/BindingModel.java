package dev.bec.hdpanel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Bounded, declarative binding fields. Text is data, never Lua or a command. */
public final class BindingModel {
    public static final int MAX_PAYLOAD=32768;
    private BindingModel() { }
    public static boolean valid(String kind,String value) {
        if(!kind.matches("uuid|side|integer|mode|path"))return false;
        if(value.length()>160 || value.indexOf('\n')>=0 || value.indexOf('\r')>=0 || value.indexOf('=')>=0)return false;
        if(value.isEmpty())return true;
        if("uuid".equals(kind))return value.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
        if("side".equals(kind))return value.matches("[0-5]");
        if("integer".equals(kind))return value.matches("[0-9]{1,10}") && Long.parseLong(value)<=Integer.MAX_VALUE;
        if("mode".equals(kind))return value.equals("cell-io")||value.equals("me-interface");
        if("path".equals(kind))return value.startsWith("/")&&!value.contains("..")&&value.matches("[a-zA-Z0-9_./-]+");
        return false;
    }
    public static JsonArray normalize(Object input) {
        JsonArray fields=new JsonArray();Set<String> seen=new HashSet<String>();
        if(!(input instanceof Map))return fields;
        Map<?,?> rows=(Map<?,?>)input;
        for(int i=1;i<=512;i++) {
            Object raw=rows.get(Double.valueOf(i));if(raw==null)raw=rows.get(Integer.valueOf(i));
            if(!(raw instanceof Map))continue;
            Map<?,?> row=(Map<?,?>)raw;
            String key=PanelModel.text(row.get("key"),48),kind=PanelModel.text(row.get("kind"),12);
            String value=PanelModel.text(row.get("value"),160);
            if(!key.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,47}")||!seen.add(key)||!valid(kind,value))
                throw new IllegalArgumentException("invalid binding field");
            JsonObject field=new JsonObject();field.addProperty("key",key);field.addProperty("kind",kind);
            field.addProperty("label",PanelModel.text(row.get("label"),64));field.addProperty("value",value);
            fields.add(field);
        }
        return fields;
    }
    public static boolean accepts(String snapshot,String payload) {
        if(payload==null||payload.length()>MAX_PAYLOAD)return false;
        try {
            JsonObject data=new JsonParser().parse(snapshot).getAsJsonObject();
            if(!data.has("bindingEditable")||!data.get("bindingEditable").getAsBoolean())return false;
            JsonArray fields=data.getAsJsonArray("bindings");if(fields==null||fields.size()==0)return false;
            String[] lines=payload.split("\n",-1);if(lines.length!=fields.size()+1)return false;
            if(!data.has("bindingRevision")||!lines[0].equals("_revision="+data.get("bindingRevision").getAsString()))return false;
            Set<String> seen=new HashSet<String>();
            for(int lineIndex=1;lineIndex<lines.length;lineIndex++) {
                String line=lines[lineIndex];
                int eq=line.indexOf('=');if(eq<1)return false;
                String key=line.substring(0,eq),value=line.substring(eq+1);boolean found=false;
                if(!seen.add(key))return false;
                for(JsonElement e:fields) {
                    JsonObject field=e.getAsJsonObject();
                    if(field.get("key").getAsString().equals(key)) {
                        if(!valid(field.get("kind").getAsString(),value))return false;
                        found=true;break;
                    }
                }
                if(!found)return false;
            }
            return true;
        } catch(RuntimeException ignored) {return false;}
    }
}
