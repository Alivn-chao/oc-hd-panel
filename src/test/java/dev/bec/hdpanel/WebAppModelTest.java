package dev.bec.hdpanel;
import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebAppModelTest{
    private Map<String,Object> app(){Map<String,Object> p=new HashMap<String,Object>();p.put("id","mine");p.put("title","我的网页");Map<String,String> f=new HashMap<String,String>();f.put("index.html","<html><body>Hello</body></html>");f.put("main.js","OC.onData(function(d){});");p.put("files",f);Map<Double,String> e=new HashMap<Double,String>();e.put(1D,"save");p.put("events",e);return p;}
    private void rejects(Runnable r){try{r.run();fail("must reject");}catch(IllegalArgumentException expected){}}
    @Test public void packageIsDeterministicAndDescriptorHasNoPageSource(){Map<String,Object> p=app();String a=WebAppModel.pack(p),b=WebAppModel.pack(app());assertEquals(a,b);String d=WebAppModel.descriptor(a);assertFalse(d.contains("<html>"));assertTrue(d.contains(WebAppModel.hash(a)));}
    @Test public void rejectsTraversalMissingEntryBinaryResourcesAndOversize(){
        Map<String,Object> p=app();p.put("entry","../index.html");rejects(()->WebAppModel.pack(p));
        p.put("entry","missing.html");rejects(()->WebAppModel.pack(p));p.put("entry","index.html");
        Map<String,String> files=(Map<String,String>)p.get("files");files.put("native.dll","binary");rejects(()->WebAppModel.pack(p));files.remove("native.dll");
        files.put("big.txt",new String(new char[WebAppModel.MAX_PACKAGE+1]).replace('\0','x'));rejects(()->WebAppModel.pack(p));
    }
    @Test public void acceptsOnlyDeclaredEventOnCurrentPage(){String d=WebAppModel.descriptor(WebAppModel.pack(app()));String hash=new JsonParser().parse(d).getAsJsonObject().get("hash").getAsString();
        assertTrue(WebAppModel.event(d,"{\"hash\":\""+hash+"\",\"event\":\"save\",\"args\":{\"target\":12}}"));
        assertFalse(WebAppModel.event(d,"{\"hash\":\"old\",\"event\":\"save\",\"args\":{}}"));
        assertFalse(WebAppModel.event(d,"{\"hash\":\""+hash+"\",\"event\":\"unknown\",\"args\":{}}"));
        assertFalse(WebAppModel.event(d,"{\"hash\":\""+hash+"\",\"event\":\"save\",\"args\":[]}"));
    }
    @Test public void rejectsDeepOrOversizedEventsBeforeJsonParse(){assertFalse(WebAppModel.boundedJson("[[[[[[[[[0]]]]]]]]]",4096));assertFalse(WebAppModel.boundedJson(new String(new char[4097]),4096));assertTrue(WebAppModel.boundedJson("{\"text\":\"[escaped \\\" bracket]\"}",4096));}
    @Test public void preservesLuaArraysAndCanonicalObjectOrder(){Map<Object,Object> rows=new HashMap<Object,Object>();rows.put(1D,12);rows.put(2D,24);Map<String,Object> d=new HashMap<String,Object>();d.put("history",rows);d.put("energy",100);String result=WebAppModel.data(d);assertEquals("{\"energy\":100,\"history\":[12,24]}",result);}
    @Test public void rejectsRecursiveNonfiniteAndHugeSnapshots(){Map<String,Object> d=new HashMap<String,Object>();d.put("value",Double.NaN);rejects(()->WebAppModel.data(d));d.put("value",d);rejects(()->WebAppModel.data(d));d.put("value",new String(new char[4097]));rejects(()->WebAppModel.data(d));}
}
