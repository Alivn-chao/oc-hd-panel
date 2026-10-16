package dev.bec.hdweb;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
public final class WebDocument {
    private static final String CSP="default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline' data:; img-src data:; font-src data:; media-src 'none'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'";
    private static final String SDK="(function(){var listeners=[],last=null,pending={},seq=1;window.__ochdData=function(d){last=d;listeners.forEach(function(f){try{f(d);}catch(e){}});};window.__ochdAck=function(a){var p=pending[a.request];if(p){delete pending[a.request];a.ok?p.resolve(a):p.reject(a);}};Object.defineProperty(window,'OC',{value:Object.freeze({onData:function(f){listeners.push(f);if(last!==null)f(last);},emit:function(name,args){return new Promise(function(resolve,reject){var id=(seq++%2147483646)+1;pending[id]={resolve:resolve,reject:reject};window.cefQuery({request:JSON.stringify({type:'event',request:id,event:name,args:args||{}}),onSuccess:function(){},onFailure:function(){}});setTimeout(function(){if(pending[id]){delete pending[id];reject({ok:false,message:'OC response timeout'});}},12000);});}})});})();";
    private static String resource(String path,String base,JsonObject files,Set<String> chain){
        if(path.startsWith("data:")||path.startsWith("#"))return path;
        if(path.contains(":")||path.startsWith("/")||path.contains(".."))return "about:blank";
        String prefix=base.contains("/")?base.substring(0,base.lastIndexOf('/')+1):"";String name=prefix+path;
        if(!files.has(name)||chain.contains(name)||chain.size()>8)return "about:blank";
        String text=files.get(name).getAsString(),mime=name.endsWith(".css")?"text/css":name.endsWith(".js")?"text/javascript":name.endsWith(".svg")?"image/svg+xml":"text/plain";
        if(name.endsWith(".css")){Set<String> next=new HashSet<String>(chain);next.add(name);text=css(text,name,files,next);}
        return "data:"+mime+";base64,"+Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    private static String css(String text,String base,JsonObject files,Set<String> chain){
        Matcher m=Pattern.compile("url\\(\\s*['\"]?([^)'\"]+)['\"]?\\s*\\)",Pattern.CASE_INSENSITIVE).matcher(text);StringBuffer b=new StringBuffer();
        while(m.find())m.appendReplacement(b,Matcher.quoteReplacement("url(\""+resource(m.group(1).trim(),base,files,chain)+"\")"));m.appendTail(b);return b.toString();
    }
    public static String document(JsonObject pack){
        JsonObject files=pack.getAsJsonObject("files");String entry=pack.get("entry").getAsString(),html=files.get(entry).getAsString();
        Matcher m=Pattern.compile("(?i)(src|href)\\s*=\\s*(['\"])(.*?)\\2").matcher(html);StringBuffer b=new StringBuffer();
        while(m.find())m.appendReplacement(b,Matcher.quoteReplacement(m.group(1)+"=\""+resource(m.group(3),entry,files,new HashSet<String>())+"\""));m.appendTail(b);html=b.toString();
        String boot="<meta http-equiv=\"Content-Security-Policy\" content=\""+(Boolean.getBoolean("ochd.selftest")?CSP.replace("script-src 'unsafe-inline'", "script-src 'unsafe-inline' 'unsafe-eval'"):CSP)+"\"><meta charset=\"utf-8\"><script>"+SDK+"</script>";
        // Prefix our head before any user markup so comments cannot swallow the CSP/bridge.
        return "<!doctype html><html><head>"+boot+"</head>"+html;

    }
}
