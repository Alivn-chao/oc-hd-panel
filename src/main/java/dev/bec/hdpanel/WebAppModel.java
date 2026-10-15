package dev.bec.hdpanel;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Data-only HTML package, bounded OC snapshots and declared event names. */
public final class WebAppModel {
    public static final int MAX_PACKAGE=262144,MAX_DATA=16384,MAX_EVENT=4096,CHUNK=16384;
    private WebAppModel(){}
    public static String hash(String text){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static boolean path(String s){return s.length()<=100&&s.matches("[A-Za-z0-9_./-]+")&&!s.startsWith("/")&&!s.contains("..")&&!s.contains("//");}
    public static String pack(Map<?,?> input){
        JsonObject out=new JsonObject();String id=PanelModel.text(input.get("id"),48),entry=input.containsKey("entry")?String.valueOf(input.get("entry")):"index.html";
        if(!id.matches("[a-zA-Z0-9_.-]{1,48}")||!path(entry)||!entry.endsWith(".html"))throw new IllegalArgumentException("invalid app id/entry");
        out.addProperty("id",id);out.addProperty("title",PanelModel.text(input.get("title"),64));out.addProperty("entry",entry);
        if(!(input.get("files")instanceof Map))throw new IllegalArgumentException("files table required");
        Map<?,?> files=(Map<?,?>)input.get("files");if(files.isEmpty()||files.size()>32)throw new IllegalArgumentException("1..32 text files required");
        JsonObject normalized=new JsonObject();TreeMap<String,String> sorted=new TreeMap<String,String>();int bytes=0;
        for(Map.Entry<?,?> e:files.entrySet()){
            if(!(e.getKey()instanceof String)||!(e.getValue()instanceof String)||!path((String)e.getKey()))throw new IllegalArgumentException("invalid text resource");
            String name=(String)e.getKey(),text=(String)e.getValue();
            if(!name.matches(".*\\.(html|css|js|svg|json|txt)$")||text.indexOf('\0')>=0)throw new IllegalArgumentException("only html/css/js/svg/json/txt resources supported in preview");
            bytes+=text.getBytes(StandardCharsets.UTF_8).length;if(bytes>MAX_PACKAGE)throw new IllegalArgumentException("package exceeds 256 KiB");sorted.put(name,text);
        }
        if(!sorted.containsKey(entry))throw new IllegalArgumentException("entry is missing");
        for(Map.Entry<String,String> e:sorted.entrySet())normalized.addProperty(e.getKey(),e.getValue());out.add("files",normalized);
        JsonArray events=new JsonArray();Set<String> names=new HashSet<String>();Object raw=input.get("events");
        if(raw!=null&&!(raw instanceof Map))throw new IllegalArgumentException("events must be an array");
        if(raw instanceof Map){Map<?,?> rows=(Map<?,?>)raw;if(rows.size()>32)throw new IllegalArgumentException("at most 32 events");
            for(int i=1;i<=32;i++){Object v=rows.get((double)i);if(v==null)v=rows.get(i);if(v==null)continue;String name=String.valueOf(v);
                if(!name.matches("[A-Za-z][A-Za-z0-9_.-]{0,47}")||!names.add(name))throw new IllegalArgumentException("invalid/duplicate event");events.add(new JsonPrimitive(name));}}
        out.add("events",events);String result=out.toString();if(result.getBytes(StandardCharsets.UTF_8).length>MAX_PACKAGE)throw new IllegalArgumentException("encoded package exceeds 256 KiB");return result;
    }
    public static String descriptor(String pack){JsonObject p=new JsonParser().parse(pack).getAsJsonObject(),d=new JsonObject();d.addProperty("id",p.get("id").getAsString());d.addProperty("title",p.get("title").getAsString());d.addProperty("hash",hash(pack));d.add("events",p.getAsJsonArray("events"));return d.toString();}
    private static JsonElement value(Object raw,int depth,int[] count){
        if(depth>8||++count[0]>512)throw new IllegalArgumentException("snapshot too complex");
        if(raw==null)return JsonNull.INSTANCE;
        if(raw instanceof Boolean)return new JsonPrimitive((Boolean)raw);
        if(raw instanceof Number){double n=((Number)raw).doubleValue();if(Double.isNaN(n)||Double.isInfinite(n))throw new IllegalArgumentException("non-finite value");return new JsonPrimitive((Number)raw);}
        if(raw instanceof String){if(((String)raw).length()>4096)throw new IllegalArgumentException("text too long");return new JsonPrimitive((String)raw);}
        if(raw instanceof Map){Map<?,?> rows=(Map<?,?>)raw;boolean array=!rows.isEmpty();
            for(Object k:rows.keySet())if(!(k instanceof Number)||((Number)k).doubleValue()!=((Number)k).intValue()||((Number)k).intValue()<1||((Number)k).intValue()>rows.size())array=false;
            if(array){JsonArray list=new JsonArray();for(int i=1;i<=rows.size();i++){Object v=rows.get((double)i);if(v==null)v=rows.get(i);list.add(value(v,depth+1,count));}return list;}
            JsonObject object=new JsonObject();TreeMap<String,Object> ordered=new TreeMap<String,Object>();for(Map.Entry<?,?> e:rows.entrySet())ordered.put(String.valueOf(e.getKey()),e.getValue());
            for(Map.Entry<String,Object> e:ordered.entrySet()){
            String key=String.valueOf(e.getKey());if(key.length()>100)throw new IllegalArgumentException("key too long");object.add(key,value(e.getValue(),depth+1,count));}return object;}
        throw new IllegalArgumentException("unsupported snapshot value");
    }
    public static String data(Map<?,?> input){JsonElement normalized=value(input,0,new int[]{0});if(!normalized.isJsonObject())throw new IllegalArgumentException("snapshot root must be an object");String result=normalized.toString();if(result.getBytes(StandardCharsets.UTF_8).length>MAX_DATA)throw new IllegalArgumentException("snapshot exceeds 16 KiB");return result;}
    public static boolean boundedJson(String text,int max){
        if(text==null||text.getBytes(StandardCharsets.UTF_8).length>max)return false;
        boolean quoted=false,escape=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>8)return false;}else if(c=='}'||c==']'){if(--depth<0)return false;}}
        return !quoted&&depth==0;
    }
    public static boolean event(String descriptor,String text){
        if(!boundedJson(text,MAX_EVENT))return false;
        try{JsonObject d=new JsonParser().parse(descriptor).getAsJsonObject(),e=new JsonParser().parse(text).getAsJsonObject();
            if(!e.get("hash").getAsString().equals(d.get("hash").getAsString())||!e.get("args").isJsonObject())return false;
            String name=e.get("event").getAsString();for(JsonElement x:d.getAsJsonArray("events"))if(name.equals(x.getAsString()))return true;
        }catch(RuntimeException ignored){}return false;
    }
}
