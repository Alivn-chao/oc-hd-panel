import com.google.gson.*;
import dev.bec.hdpanel.client.WebHostProcess;
import java.io.File;
import java.awt.image.BufferedImage;

/** Exercises the actual Minecraft-side process wrapper, without a Minecraft world. */
public class ClientBridgeProbe {
    public static void main(String[] args)throws Exception{
        WebHostProcess host=new WebHostProcess(new File(args[0]));
        try{
            JsonObject p=new JsonObject(),files=new JsonObject(),load=new JsonObject();p.addProperty("entry","index.html");
            files.addProperty("index.html","<html><head></head><body style='background:#6688ff'><h1 id='value'></h1><script>OC.onData(function(d){document.getElementById('value').textContent=d.value;});setTimeout(function(){OC.emit('probe',{value:42});},100);</script></body></html>");p.add("files",files);load.addProperty("type","load");load.add("package",p);host.send(load);
            JsonObject data=new JsonObject(),values=new JsonObject();values.addProperty("value",42);data.addProperty("type","data");data.add("data",values);host.send(data);
            long deadline=System.currentTimeMillis()+20000;BufferedImage frame=null;JsonObject event=null;
            while(System.currentTimeMillis()<deadline&&(frame==null||event==null)){
                JsonObject snap=new JsonObject();snap.addProperty("type","snapshot");host.send(snap);Thread.sleep(150);
                BufferedImage latest=host.frame.getAndSet(null);if(latest!=null)frame=latest;JsonObject next=host.events.poll();if(next!=null)event=next;
            }
            if(frame==null||event==null)throw new AssertionError("client wrapper did not receive frame/event: "+host.error);
            if(frame.getWidth()!=1920||frame.getHeight()!=1080||!event.get("event").getAsString().equals("probe"))throw new AssertionError("bad bridge output");
            System.out.println("PASS: production client process launcher, binary frame reader and event queue");
        }finally{host.close();}
    }
}
