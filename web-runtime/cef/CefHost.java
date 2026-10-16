package dev.bec.hdweb;

import com.google.gson.*;
import dev.bec.hdpanel.WebFrameCodec;
import dev.bec.hdpanel.WebMappedFrames;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.*;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.network.*;

/** Isolated Chromium/CEF host. Local pages only; bounded OC JSON bridge.
 * Native-resolution OSR paint events replace timed WebKit screenshots.
 * Shared memory uses acknowledged slots, without per-frame zlib or PNG. */
public final class CefHost {
    private final DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)));
    private final WebMappedFrames mapping;
    private final boolean[] busy=new boolean[2];
    private byte[] pending;
    private volatile byte[] lastFrame;
    private volatile int fps;
    private volatile boolean loaded;
    private OCHDBrowser browser;
    private String latest="{}";
    private final CountDownLatch created=new CountDownLatch(1);
    private CefApp cef;
    private Path profile;
    private boolean closing;
    private final CountDownLatch terminated=new CountDownLatch(1);
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"OCHD-heartbeat");t.setDaemon(true);return t;});
    private CefHost()throws IOException {
        String path=System.getProperty("ochd.web.frames");mapping=path==null?null:new WebMappedFrames(new File(path),false);
    }
    private synchronized void send(int type,byte[] bytes)throws IOException {out.writeByte(type);out.writeInt(bytes.length);out.write(bytes);out.flush();}
    private void record(String type,String message){JsonObject o=new JsonObject();o.addProperty("type",type);o.addProperty("message",message);try{send(2,o.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException e){System.exit(0);}}
    private void script(String js){browser.executeJavaScript(js,"about:blank",0);}
    private void data(){if(loaded)script("window.__ochdData&&window.__ochdData("+latest+");");}
    private synchronized void paint(ByteBuffer pixels,int width,int height) {
        if(width!=1920||height!=1080){record("error","Unexpected Chromium viewport "+width+"x"+height);return;}
        // Keep the final paint even while the consumer is busy or streaming is paused.
        if(lastFrame==null)lastFrame=new byte[WebMappedFrames.BYTES];byte[] bytes=lastFrame;pixels.duplicate().get(bytes);
        if(fps>0&&loaded)publish(bytes);
    }
    private synchronized void publish(byte[] bytes) {
        try {
            if(mapping!=null){
                int slot=!busy[0]?0:!busy[1]?1:-1;
                if(slot<0){pending=bytes;return;}
                busy[slot]=true;mapping.write(slot,ByteBuffer.wrap(bytes));
                send(4,new byte[]{(byte)slot});
            }else {
                // Compatibility/test transport. Production clients use shared memory.
                int[] argb=new int[1920*1080];ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(argb);
                send(3,WebFrameCodec.encode(argb));
            }
        }catch(IOException e){record("error","Frame transport: "+e.getMessage());}
    }
    private synchronized void acknowledge(int slot) {
        if(slot<0||slot>1)return;busy[slot]=false;
        if(pending!=null){byte[] bytes=pending;pending=null;publish(bytes);}
    }
    private static boolean local(String url){return url.equals("about:blank")||url.startsWith("data:");}
    private void init(File root)throws Exception {
        CefAppBuilder builder=new CefAppBuilder();builder.setInstallDir(new File(root,"native"));builder.setSkipInstallation(true);
        profile=Files.createTempDirectory("ochd-cef-");
        builder.getCefSettings().root_cache_path=profile.toString();
        builder.setAppHandler(new MavenCefAppHandlerAdapter(){@Override public void stateHasChanged(CefApp.CefAppState state){if(state==CefApp.CefAppState.TERMINATED)terminated.countDown();}});
        builder.getCefSettings().windowless_rendering_enabled=true;
        builder.getCefSettings().log_severity=CefSettings.LogSeverity.LOGSEVERITY_ERROR;
        builder.getCefSettings().log_file=new File(root,"cef.log").getAbsolutePath();
        builder.addJcefArgs("--force-device-scale-factor=1.5","--disable-background-networking","--disable-component-update","--disable-extensions","--disable-features=Translate","--autoplay-policy=user-gesture-required");
        cef=builder.build();CefClient client=cef.createClient();
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){
            @Override public void onAfterCreated(CefBrowser b){created.countDown();}
            @Override public boolean onBeforePopup(CefBrowser b,CefFrame f,String url,String name){return true;}
        });
        client.addRequestHandler(new CefRequestHandlerAdapter(){
            @Override public void onRenderProcessTerminated(CefBrowser b,CefRequestHandler.TerminationStatus status,int code,String message){record("error","Chromium renderer stopped: "+status+" ("+code+")");}
            @Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest r,boolean gesture,boolean redirect){return !local(r.getURL());}
            @Override public boolean onOpenURLFromTab(CefBrowser b,CefFrame f,String url,boolean gesture){return true;}
            @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest r,boolean nav,boolean download,String initiator,org.cef.misc.BoolRef disable){
                return new CefResourceRequestHandlerAdapter(){@Override public boolean onBeforeResourceLoad(CefBrowser b,CefFrame f,CefRequest r){return !local(r.getURL());}};
            }
        });
        client.addContextMenuHandler(new CefContextMenuHandlerAdapter(){@Override public void onBeforeContextMenu(CefBrowser b,CefFrame f,CefContextMenuParams p,CefMenuModel model){model.clear();}});
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouterHandlerAdapter(){
            @Override public boolean onQuery(CefBrowser b,CefFrame f,long id,String request,boolean persistent,CefQueryCallback cb){
                if(request.length()>4096){cb.failure(1,"Message too large");return true;}
                try {JsonObject event=new JsonParser().parse(request).getAsJsonObject();String type=event.get("type").getAsString();
                    if(type.equals("event")||(type.equals("eval")&&Boolean.getBoolean("ochd.selftest")))send(2,request.getBytes(StandardCharsets.UTF_8));
                    else {cb.failure(1,"Unsupported message");return true;}
                    cb.success("");
                }catch(Exception e){cb.failure(1,"Invalid message");}return true;
            }
        }));
        client.addLoadHandler(new CefLoadHandlerAdapter(){
            @Override public void onLoadEnd(CefBrowser b,CefFrame f,int status){if(!f.isMain()||b.getURL().equals("about:blank"))return;loaded=true;data();record("ready","Chromium HTML loaded");}
        });
        browser=new OCHDBrowser(client,this::paint);browser.createImmediately();
        if(!created.await(30,TimeUnit.SECONDS))throw new IOException("Chromium browser creation timed out");
        browser.setWindowlessFrameRate(60);browser.setFocus(true);record("started","Chromium/CEF 146 ready; native 1080p");
        heartbeat.scheduleAtFixedRate(()->record("heartbeat",""),5,5,TimeUnit.SECONDS);
    }
    private void command(JsonObject c)throws Exception {
        String type=c.get("type").getAsString();
        if(type.equals("load")){loaded=false;browser.loadURL("data:text/html;base64,"+Base64.getEncoder().encodeToString(WebDocument.document(c.getAsJsonObject("package")).getBytes(StandardCharsets.UTF_8)));}
        else if(type.equals("data")){latest=c.get("data").toString();data();}
        else if(type.equals("zoom")){double z=c.get("value").getAsDouble();if(Double.isFinite(z))browser.setZoomLevel(Math.log(Math.max(.5,Math.min(2.5,z)))/Math.log(1.2));}
        else if(type.equals("stream")){fps=Math.max(0,Math.min(60,c.get("fps").getAsInt()));browser.setWindowlessFrameRate(Math.max(1,fps));if(fps>0&&lastFrame!=null)publish(lastFrame);}
        else if(type.equals("frame_ack"))acknowledge(c.get("slot").getAsInt());
        else if(type.equals("snapshot")){if(lastFrame!=null)publish(lastFrame);record("snapshot_done","");}
        else if(type.equals("ack"))script("window.__ochdAck&&window.__ochdAck("+c.get("data").toString()+");");
        else if(type.equals("eval")&&Boolean.getBoolean("ochd.selftest"))script("window.cefQuery({request:JSON.stringify({type:'eval',message:String((0,eval)("+new JsonPrimitive(c.get("script").getAsString())+"))}),onSuccess:function(){},onFailure:function(){}})");
        else if(type.equals("input"))input(c);
        else if(type.equals("close"))closing=true;
    }
    private int buttons;
    private static boolean flag(JsonObject c,String name){return c.has(name)&&c.get(name).getAsBoolean();}
    private void input(JsonObject c)throws Exception {
        String kind=c.get("kind").getAsString();int modifiers=(flag(c,"shift")?InputEvent.SHIFT_DOWN_MASK:0)|(flag(c,"ctrl")?InputEvent.CTRL_DOWN_MASK:0)|(flag(c,"alt")?InputEvent.ALT_DOWN_MASK:0);
        if(kind.equals("paste")){String text=c.get("text").getAsString();if(text.length()<=4096)script("(function(s){var e=document.activeElement;if(e&&(e.tagName==='INPUT'||e.tagName==='TEXTAREA')){e.setRangeText(s,e.selectionStart||0,e.selectionEnd||0,'end');e.dispatchEvent(new Event('input',{bubbles:true}));}})("+new JsonPrimitive(text)+");");return;}
        long now=System.currentTimeMillis();
        if(kind.equals("key")){
            String key=c.get("key").getAsString();if(key.startsWith("DIGIT"))key=key.substring(5);
            int code;try{code=KeyEvent.class.getField("VK_"+key).getInt(null);}catch(Exception e){code=KeyEvent.VK_UNDEFINED;}
            String ch=c.has("character")?c.get("character").getAsString():"";char character=ch.isEmpty()?KeyEvent.CHAR_UNDEFINED:ch.charAt(0);
            int scan=c.has("scan")?c.get("scan").getAsInt():0;browser.setFocus(true);
            browser.key(new BrowserKeyEvent(browser.getUIComponent(),KeyEvent.KEY_PRESSED,now,modifiers,code,character,scan));
            if(!ch.isEmpty()&&!flag(c,"ctrl")&&!flag(c,"alt"))browser.key(new BrowserKeyEvent(browser.getUIComponent(),KeyEvent.KEY_TYPED,now,modifiers,KeyEvent.VK_UNDEFINED,character,scan));
            browser.key(new BrowserKeyEvent(browser.getUIComponent(),KeyEvent.KEY_RELEASED,now,modifiers,code,character,scan));return;
        }
        int x=c.get("x").getAsInt(),y=c.get("y").getAsInt();
        if(kind.equals("wheel")){
            double delta=c.get("delta").getAsDouble();if(delta==0||!Double.isFinite(delta))return;
            // JCEF sends AWT units directly as CEF pixels (not Windows wheel
            // notches). Match the 1.5x viewport: one legacy OC notch (80) is
            // 180 CEF units (about 120-150 CSS pixels), with Chromium's wheel sign.
            int amount=(int)Math.max(1,Math.min(1080,Math.round(Math.abs(delta)*2.25)));
            browser.wheel(new MouseWheelEvent(browser.getUIComponent(),MouseEvent.MOUSE_WHEEL,now,modifiers|buttons,x,y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,amount,delta>0?1:-1));return;
        }
        int index=c.has("button")?c.get("button").getAsInt():0;if(index<0||index>2)return;
        int button=index==0?MouseEvent.BUTTON1:index==1?MouseEvent.BUTTON3:MouseEvent.BUTTON2,mask=InputEvent.getMaskForButton(button);
        boolean down=kind.equals("down"),up=kind.equals("up");if(down){browser.setFocus(true);buttons|=mask;}if(up)buttons&=~mask;
        int id=down?MouseEvent.MOUSE_PRESSED:up?MouseEvent.MOUSE_RELEASED:buttons!=0?MouseEvent.MOUSE_DRAGGED:MouseEvent.MOUSE_MOVED;
        browser.mouse(new MouseEvent(browser.getUIComponent(),id,now,modifiers|buttons,x,y,down||up?1:0,false,down||up?button:MouseEvent.NOBUTTON));
    }
    public static void main(String[] args)throws Exception {
        System.setOut(new PrintStream(System.err,true,"UTF-8"));
        CefHost host=new CefHost();host.init(new File(System.getProperty("ochd.web.root",".")));
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        try {String line;while(!host.closing&&(line=in.readLine())!=null){if(line.length()>400000)throw new IOException("Command too large");try{host.command(new JsonParser().parse(line).getAsJsonObject());}catch(Exception e){host.record("error",e.toString());}}}
        finally {
            host.browser.close(true);host.cef.dispose();host.terminated.await(4,TimeUnit.SECONDS);
            if(host.mapping!=null)host.mapping.close();
            // Only the temporary directory created by this host is removed;
            // walkFileTree does not follow symbolic links.
            try{Files.walkFileTree(host.profile,new SimpleFileVisitor<Path>(){
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{Files.delete(path);return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult postVisitDirectory(Path path,IOException e)throws IOException{Files.delete(path);return FileVisitResult.CONTINUE;}
            });}catch(IOException ignored){}
            System.exit(0);
        }
    }
}
