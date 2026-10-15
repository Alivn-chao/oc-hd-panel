package dev.bec.hdweb;

import com.google.gson.*;
import dev.bec.hdpanel.WebCommandQueue;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.scene.web.*;

/** Isolated client WebKit host. stdin JSON commands, stdout length-prefixed PNG/JSON. */
public final class Host {
    private final DataOutputStream out=new DataOutputStream(new BufferedOutputStream(System.out));
    private WebView view;private WebEngine engine;private javax.swing.JWindow window;private String latest="{}";private boolean loaded,mouseDown;
    private final WebCommandQueue inbox=new WebCommandQueue(128);
    private static final int MAX_LINE=400000;
    private static final String CSP="default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline' data:; img-src data:; font-src data:; media-src 'none'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'";
    private static final String SDK="(function(){var listeners=[],last=null,pending={},seq=1;window.__ochdData=function(d){last=d;listeners.forEach(function(f){try{f(d);}catch(e){}});};window.__ochdAck=function(a){var p=pending[a.request];if(p){delete pending[a.request];a.ok?p.resolve(a):p.reject(a);}};Object.defineProperty(window,'OC',{value:Object.freeze({onData:function(f){listeners.push(f);if(last!==null)f(last);},emit:function(name,args){return new Promise(function(resolve,reject){var id=(seq++%2147483646)+1;pending[id]={resolve:resolve,reject:reject};window.status=JSON.stringify({type:'event',request:id,event:name,args:args||{}});setTimeout(function(){if(pending[id]){delete pending[id];reject({ok:false,message:'OC response timeout'});}},12000);});}})});})();";
    private void send(int type,byte[] bytes)throws IOException{ synchronized(out){out.writeByte(type);out.writeInt(bytes.length);out.write(bytes);out.flush();} }
    private void record(String type,String message){JsonObject o=new JsonObject();o.addProperty("type",type);o.addProperty("message",message);try{send(2,o.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException e){Platform.exit();}}
    private void init(){
        Platform.setImplicitExit(false);view=new WebView();view.setPrefSize(1280,720);view.setMinSize(1280,720);view.setMaxSize(1280,720);engine=view.getEngine();engine.setJavaScriptEnabled(true);
        // No Java objects are exposed to page scripts. Only bounded JSON status messages cross the bridge.
        engine.setOnStatusChanged(e->{String s=e.getData();if(s==null||s.length()>4096)return;try{JsonObject event=new JsonParser().parse(s).getAsJsonObject();if("event".equals(event.get("type").getAsString()))send(2,s.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}});
        engine.setCreatePopupHandler(features->null);engine.setConfirmHandler(value->false);engine.setPromptHandler(value->null);
        engine.locationProperty().addListener((o,a,b)->{if(b!=null&&!b.isEmpty()&&!b.equals("about:blank")){engine.getLoadWorker().cancel();record("error","Navigation disabled in local dashboard preview");}});
        engine.getLoadWorker().stateProperty().addListener((o,a,b)->{if(b==Worker.State.SUCCEEDED){loaded=true;data();record("ready","HTML loaded");}else if(b==Worker.State.FAILED)record("error","HTML load failed");});
        StackPane root=new StackPane(view);Scene scene=new Scene(root,1280,720);
        // A non-focusable transparent Swing host keeps WebKit painting without stealing Minecraft focus.
        final javafx.embed.swing.JFXPanel[] holder=new javafx.embed.swing.JFXPanel[1];
        try{javax.swing.SwingUtilities.invokeAndWait(()->{
            window=new javax.swing.JWindow();window.setType(java.awt.Window.Type.UTILITY);window.setFocusableWindowState(false);window.setAutoRequestFocus(false);
            holder[0]=new javafx.embed.swing.JFXPanel();window.add(holder[0]);window.setSize(1280,720);window.setLocation(-10000,-10000);window.setOpacity(0F);
        });}catch(Exception e){throw new IllegalStateException(e);}
        holder[0].setScene(scene);javax.swing.SwingUtilities.invokeLater(()->window.setVisible(true));view.requestFocus();
        javafx.animation.Timeline timer=new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(20),e->{for(int i=0;i<8;i++){String line=inbox.poll();if(line==null)break;try{command(new JsonParser().parse(line).getAsJsonObject());}catch(Throwable ex){record("error",ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage()));}}}));timer.setCycleCount(javafx.animation.Animation.INDEFINITE);timer.play();
        record("started","WebKit host ready");
    }
    private void data(){if(loaded)engine.executeScript("window.__ochdData&&window.__ochdData(JSON.parse("+new JsonPrimitive(latest).toString()+"));");}
    private void snapshot()throws Exception{
        if(!loaded)return;view.applyCss();view.layout();SnapshotParameters params=new SnapshotParameters();params.setTransform(new Scale(1.5,1.5));
        WritableImage shot=view.snapshot(params,new WritableImage(1920,1080));BufferedImage image=SwingFXUtils.fromFXImage(shot,null);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);send(1,bytes.toByteArray());
    }
    private void command(JsonObject c)throws Exception{
        String type=c.get("type").getAsString();
        if(type.equals("load")){loaded=false;engine.loadContent(document(c.getAsJsonObject("package")));}
        else if(type.equals("data")){latest=c.get("data").toString();data();}
        else if(type.equals("zoom")){double z=c.get("value").getAsDouble();if(Double.isFinite(z))view.setZoom(Math.max(.5,Math.min(2.5,z)));}
        else if(type.equals("snapshot")){try{snapshot();}finally{record("snapshot_done","");}}
        else if(type.equals("ack"))engine.executeScript("window.__ochdAck&&window.__ochdAck("+c.get("data").toString()+");");
        else if(type.equals("input"))input(c);
        else if(type.equals("eval")&&Boolean.getBoolean("ochd.selftest")){Object result=engine.executeScript(c.get("script").getAsString());record("eval",String.valueOf(result));}
        else if(type.equals("close")){javax.swing.SwingUtilities.invokeLater(()->window.dispose());Platform.exit();System.exit(0);}
    }
    private static boolean flag(JsonObject c,String name){return c.has(name)&&c.get(name).getAsBoolean();}
    private void input(JsonObject c){
        String kind=c.get("kind").getAsString();boolean shift=flag(c,"shift"),ctrl=flag(c,"ctrl"),alt=flag(c,"alt");
        if(kind.equals("paste")){String text=c.get("text").getAsString();if(text.length()>4096)return;engine.executeScript("(function(s){var e=document.activeElement;if(e&&(e.tagName==='INPUT'||e.tagName==='TEXTAREA')){e.setRangeText(s,e.selectionStart||0,e.selectionEnd||0,'end');e.dispatchEvent(new Event('input',{bubbles:true}));}})("+new JsonPrimitive(text)+");");return;}
        if(kind.equals("key")){String name=c.get("key").getAsString();KeyCode code;try{code=KeyCode.valueOf(name);}catch(IllegalArgumentException unknown){code=KeyCode.UNDEFINED;}String character=c.has("character")?c.get("character").getAsString():"";
            Event.fireEvent(view,new KeyEvent(KeyEvent.KEY_PRESSED,character,character,code,shift,ctrl,alt,false));
            if(!character.isEmpty()&&!ctrl&&!alt)Event.fireEvent(view,new KeyEvent(KeyEvent.KEY_TYPED,character,character,KeyCode.UNDEFINED,shift,ctrl,alt,false));
            Event.fireEvent(view,new KeyEvent(KeyEvent.KEY_RELEASED,character,character,code,shift,ctrl,alt,false));return;}
        double x=c.get("x").getAsDouble(),y=c.get("y").getAsDouble();
        if(kind.equals("wheel")){Event.fireEvent(view,new ScrollEvent(ScrollEvent.SCROLL,x,y,x,y,shift,ctrl,alt,false,false,false,0,c.get("delta").getAsDouble(),0,0,ScrollEvent.HorizontalTextScrollUnits.NONE,0,ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));return;}
        MouseButton b=MouseButton.PRIMARY;boolean down=kind.equals("down");if(down)mouseDown=true;else if(kind.equals("up"))mouseDown=false;
        javafx.event.EventType<MouseEvent> event=down?MouseEvent.MOUSE_PRESSED:kind.equals("up")?MouseEvent.MOUSE_RELEASED:mouseDown?MouseEvent.MOUSE_DRAGGED:MouseEvent.MOUSE_MOVED;
        Event.fireEvent(view,new MouseEvent(event,x,y,x,y,down?b:kind.equals("up")?b:MouseButton.NONE,1,shift,ctrl,alt,false,mouseDown,false,false,true,false,false,null));
        if(kind.equals("up"))Event.fireEvent(view,new MouseEvent(MouseEvent.MOUSE_CLICKED,x,y,x,y,b,1,shift,ctrl,alt,false,false,false,false,true,false,true,null));
    }
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
    private static String document(JsonObject pack){
        JsonObject files=pack.getAsJsonObject("files");String entry=pack.get("entry").getAsString(),html=files.get(entry).getAsString();
        Matcher m=Pattern.compile("(?i)(src|href)\\s*=\\s*(['\"])(.*?)\\2").matcher(html);StringBuffer b=new StringBuffer();
        while(m.find())m.appendReplacement(b,Matcher.quoteReplacement(m.group(1)+"=\""+resource(m.group(3),entry,files,new HashSet<String>())+"\""));m.appendTail(b);html=b.toString();
        String boot="<meta http-equiv=\"Content-Security-Policy\" content=\""+CSP+"\"><meta charset=\"utf-8\"><script>"+SDK+"</script>";
        // Prefix our head before any user markup so comments cannot swallow the CSP/bridge.
        return "<!doctype html><html><head>"+boot+"</head>"+html;

    }
    public static void main(String[] args)throws Exception{
        Host host=new Host();Platform.startup(host::init);BufferedReader reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        while(true){StringBuilder line=new StringBuilder();int ch;while((ch=reader.read())!=-1&&ch!='\n'){if(line.length()>=MAX_LINE)throw new IOException("command too long");line.append((char)ch);}if(ch==-1)break;host.inbox.put(new JsonParser().parse(line.toString()).getAsJsonObject());}
        Platform.exit();System.exit(0);
    }
}
