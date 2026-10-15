package dev.bec.hdpanel.client;

import com.google.gson.*;
import dev.bec.hdpanel.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Preview limit: one active browser per client, 8 content-addressed package cache entries. */
public final class WebScreens {
    private static final LinkedHashMap<String,String> CACHE=new LinkedHashMap<String,String>(8,.75F,true);
    private static final Map<String,byte[][]> CHUNKS=new HashMap<String,byte[][]>();
    private static final Map<String,Long> REQUESTS=new HashMap<String,Long>();
    private static final ArrayBlockingQueue<WebAssetPacket> RECEIVED=new ArrayBlockingQueue<WebAssetPacket>(32);
    private static View current;
    private WebScreens(){}
    public static void receive(WebAssetPacket packet){RECEIVED.offer(packet);}
    public static View get(PanelTile tile,boolean gui){
        tile=tile.master();long now=System.currentTimeMillis();
        if(current==null||current.tile!=tile){if(current!=null&&!gui&&now-current.lastUsed<5000)return null;close();current=new View(tile);}
        current.lastUsed=now;try{current.refresh(gui);}catch(RuntimeException e){current.error="网页数据暂时无效："+e.getClass().getSimpleName();}return current;
    }
    public static void close(){if(current!=null)current.dispose();current=null;}
    public static void tick(){
        WebAssetPacket packet;while((packet=RECEIVED.poll())!=null){if(!REQUESTS.containsKey(packet.hash))continue;
            byte[][] parts=CHUNKS.get(packet.hash);if(parts==null){parts=new byte[packet.total][];CHUNKS.put(packet.hash,parts);}if(parts.length!=packet.total)continue;parts[packet.index]=packet.data;
            boolean complete=true;int size=0;for(byte[] part:parts){if(part==null){complete=false;break;}size+=part.length;}
            if(complete){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream(size);for(byte[] part:parts)bytes.write(part);String text=new String(bytes.toByteArray(),"UTF-8");
                if(size<=WebAppModel.MAX_PACKAGE&&WebAppModel.hash(text).equals(packet.hash)){CACHE.put(packet.hash,text);while(CACHE.size()>8)CACHE.remove(CACHE.keySet().iterator().next());}}
                catch(Exception ignored){}CHUNKS.remove(packet.hash);REQUESTS.remove(packet.hash);}
        }
        long now=System.currentTimeMillis();Iterator<Map.Entry<String,Long>> it=REQUESTS.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Long> e=it.next();if(now-e.getValue()>10000){CHUNKS.remove(e.getKey());it.remove();}}
        if(current==null)return;Minecraft mc=Minecraft.getMinecraft();PanelTile tile=current.tile;
        if(mc.theWorld==null||tile.isInvalid()||tile.getWorldObj()!=mc.theWorld||!"web".equals(tile.app)||now-current.lastUsed>15000){close();return;}
        if(current.host!=null){JsonObject event;for(int i=0;i<4&&(event=current.host.events.poll())!=null;i++)current.event(event);}
    }
    public static void reply(ActionReplyPacket reply){if(current!=null&&reply.x==current.tile.xCoord&&reply.y==current.tile.yCoord&&reply.z==current.tile.zCoord)current.reply(reply);}
    public static final class View {
        public final PanelTile tile;private WebHostProcess host;private String hash="",lastValues="",loaded="";private boolean lastOnline;
        private DynamicTexture texture;private ResourceLocation location;private long lastUsed,lastSnapshot,lastLoadTry,lastEvent;private int pending,browserRequest;
        public String error="正在下载页面包…";
        private View(PanelTile tile){this.tile=tile;}
        public ResourceLocation location(){return location;}
        private void refresh(boolean gui){
            JsonObject descriptor;try{descriptor=new JsonParser().parse(tile.layout).getAsJsonObject();hash=descriptor.get("hash").getAsString();if(!hash.matches("[0-9a-f]{64}"))return;}catch(RuntimeException e){error="页面描述无效";return;}
            long now=System.currentTimeMillis();String source=CACHE.get(hash);
            if(source==null){if(!REQUESTS.containsKey(hash)){REQUESTS.put(hash,now);HdPanelMod.NETWORK.sendToServer(new WebAssetRequest(tile,hash));}return;}
            if(host==null){if(now-lastLoadTry<10000)return;lastLoadTry=now;host=new WebHostProcess(new File(Minecraft.getMinecraft().mcDataDir,"ochd-web-runtime"));}
            if(!loaded.equals(hash)){JsonObject cmd=new JsonObject();cmd.addProperty("type","load");cmd.add("package",new JsonParser().parse(source));if(host.send(cmd)){loaded=hash;lastValues="";pending=0;}}
            if(!lastValues.equals(tile.values)||lastOnline!=tile.online){JsonObject cmd=new JsonObject();cmd.addProperty("type","data");JsonObject data=new JsonParser().parse(tile.values).getAsJsonObject();data.addProperty("_online",tile.online);cmd.add("data",data);if(host.send(cmd)){lastValues=tile.values;lastOnline=tile.online;}}
            if(now-lastSnapshot>=(gui?100:500)){JsonObject cmd=new JsonObject();cmd.addProperty("type","snapshot");if(host.send(cmd))lastSnapshot=now;}
            BufferedImage image=host.frame.getAndSet(null);if(image!=null){if(texture==null){texture=new DynamicTexture(1920,1080);location=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("ochd-html",texture);}image.getRGB(0,0,1920,1080,texture.getTextureData(),0,1920);texture.updateDynamicTexture();
                Minecraft.getMinecraft().getTextureManager().bindTexture(location);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);}
            error=host.error;if(now-host.lastMessage>20000){host.close();host=null;loaded="";error="网页运行时无响应，已关闭；稍后重试";}
        }
        public void input(JsonObject command){if(host!=null){command.addProperty("type","input");host.send(command);}}
        private void event(JsonObject e){
            try{int request=e.get("request").getAsInt();if(request<=0)return;
                JsonObject packet=new JsonObject();packet.addProperty("hash",hash);packet.add("event",e.get("event"));packet.add("args",e.get("args"));
                Minecraft mc=Minecraft.getMinecraft();boolean focused=mc.currentScreen instanceof WebPanelGui&&((WebPanelGui)mc.currentScreen).tile==tile;
                if(!focused||!tile.online||pending!=0||System.currentTimeMillis()-lastEvent<250||!WebAppModel.event(tile.layout,packet.toString())){ack(request,false,"页面离线、非交互状态或仍有请求待确认");return;}
                lastEvent=System.currentTimeMillis();browserRequest=request;pending=new java.util.Random().nextInt(Integer.MAX_VALUE-1)+1;HdPanelMod.NETWORK.sendToServer(ActionPacket.web(tile,pending,packet.toString()));
            }catch(RuntimeException ignored){}
        }
        private void ack(int request,boolean ok,String message){if(host==null)return;JsonObject cmd=new JsonObject(),data=new JsonObject();cmd.addProperty("type","ack");data.addProperty("request",request);data.addProperty("ok",ok);data.addProperty("message",message);cmd.add("data",data);host.send(cmd);}
        private void reply(ActionReplyPacket reply){if(reply.request!=pending||reply.state==0)return;ack(browserRequest,reply.state==1,reply.message);pending=0;}
        private void dispose(){if(host!=null)host.close();if(location!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(location);}
    }
}
