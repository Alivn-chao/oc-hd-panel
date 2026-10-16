import com.google.gson.*;
import dev.bec.hdpanel.client.WebHostProcess;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.awt.image.BufferedImage;

/** Exercise the production Java bridge and shared-memory acknowledgement path.
 * Pass the runtime directory and the BEC web_page.html path. No Minecraft or
 * hardware automation is started. Frame rates exclude GL texture uploads. */
public final class ClientProbe {
    private static JsonObject command(String type){JsonObject c=new JsonObject();c.addProperty("type",type);return c;}
    public static void main(String[] args)throws Exception {
        WebHostProcess host=new WebHostProcess(new File(args[0]));
        try {
            String html=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
            JsonObject pack=new JsonObject(),files=new JsonObject();files.addProperty("index.html",html);pack.add("files",files);pack.addProperty("entry","index.html");
            JsonObject load=command("load");load.add("package",pack);host.send(load);
            JsonObject stream=command("stream");stream.addProperty("fps",60);host.send(stream);
            long deadline=System.nanoTime()+30_000_000_000L;
            while(host.frame.get()==null&&System.nanoTime()<deadline)Thread.sleep(10);
            if(host.frame.get()==null)throw new AssertionError(host.error);
            Thread.sleep(1500);host.frame.set(null);
            JsonObject down=command("input");down.addProperty("kind","down");down.addProperty("button",0);down.addProperty("x",600);down.addProperty("y",235);host.send(down);
            int frames=0,changes=0,last=0,move=0;long start=System.nanoTime(),nextMove=start;
            while(System.nanoTime()-start<5_000_000_000L){
                long now=System.nanoTime();
                if(now>=nextMove){JsonObject c=command("input");c.addProperty("kind","move");c.addProperty("x",600+(int)(Math.sin(move*.05)*120));c.addProperty("y",235+(int)(Math.cos(move*.03)*35));host.send(c);move++;nextMove=now+16_000_000;}
                BufferedImage image=host.frame.getAndSet(null);
                if(image!=null){frames++;int hash=1;for(int y=200;y<500;y+=11)for(int x=400;x<1100;x+=17)hash=31*hash+image.getRGB(x,y);if(hash!=last)changes++;last=hash;}
                Thread.sleep(1);
            }
            JsonObject up=command("input");up.addProperty("kind","up");up.addProperty("button",0);up.addProperty("x",600);up.addProperty("y",235);host.send(up);
            System.out.println("Production Java/shared-memory bridge: "+frames+" frames / 5 s = "+frames/5.0+" FPS; changing="+changes+"; moves="+move+"; status="+host.error);
            if(frames<60||changes<50)throw new AssertionError("Expected continuously changing 1080p frames");
            // BEC has CSS/flow animations; use an actually static document here.
            files.addProperty("index.html","<html><body style='background:#123456'>Static paint test</body></html>");
            host.send(load);Thread.sleep(1800);host.frame.set(null);Thread.sleep(1000);
            if(host.frame.get()!=null)throw new AssertionError("Static page resent a frame");
            System.out.println("PASS: continuous frames, acknowledgement flow and static paint suppression");
        }finally{host.close();}
    }
}
