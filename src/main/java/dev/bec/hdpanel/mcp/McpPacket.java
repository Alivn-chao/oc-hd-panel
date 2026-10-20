package dev.bec.hdpanel.mcp;

import com.google.gson.*;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.bec.hdpanel.HdPanelMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

public class McpPacket implements IMessage {
    public int id; public String json="{}";
    public McpPacket() {}
    public McpPacket(int id,String json) {this.id=id;this.json=json;}
    public void fromBytes(ByteBuf b) { id=b.readInt();int n=b.readInt();if(n<0 || n>McpLimits.PACKET || n>b.readableBytes())throw new IllegalArgumentException("invalid MCP packet");byte[] bytes=new byte[n];b.readBytes(bytes);json=new String(bytes,StandardCharsets.UTF_8); }
    public void toBytes(ByteBuf b) {byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>McpLimits.PACKET)throw new IllegalArgumentException("MCP response too large");b.writeInt(id);b.writeInt(bytes.length);b.writeBytes(bytes);}
    public static class Reply extends McpPacket {
        public Reply() {} public Reply(int id,String json){super(id,json);}
    }
    public static class ClientHandler implements IMessageHandler<Reply,IMessage> {
        public IMessage onMessage(Reply p,MessageContext c) {HdPanelMod.proxy.receiveMcp(p);return null;}
    }
    public static class ServerHandler implements IMessageHandler<McpPacket,IMessage> {
        private static final java.util.Map<EntityPlayerMP,long[]> RATE=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<EntityPlayerMP,long[]>());
        private static final ArrayBlockingQueue<Work> QUEUE=new ArrayBlockingQueue<Work>(16);
        private static class Work {final McpPacket p;final EntityPlayerMP player;final long time=System.currentTimeMillis();Work(McpPacket p,EntityPlayerMP player){this.p=p;this.player=player;}}
        public IMessage onMessage(McpPacket p,MessageContext c) {
            EntityPlayerMP player=c.getServerHandler().playerEntity;
            synchronized(RATE){long now=System.currentTimeMillis();long[] rate=RATE.get(player);if(rate==null||now-rate[0]>=1000){rate=new long[]{now,0};RATE.put(player,rate);}if(++rate[1]>12)return new Reply(p.id,McpService.error("MCP rate limit: 12 requests per second").toString());}
            if(!QUEUE.offer(new Work(p,player)))return new Reply(p.id,McpService.error("server bridge busy").toString());return null;
        }
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            long start=System.nanoTime();
            for(int i=0;i<2;i++) {
                Work w=QUEUE.poll();if(w==null)return;
                if(w.player.playerNetServerHandler==null || !w.player.playerNetServerHandler.netManager.isChannelOpen() || System.currentTimeMillis()-w.time>10000)continue;
                String result;
                try{result=McpService.handle(w.player,new JsonParser().parse(w.p.json).getAsJsonObject()).toString();if(result.getBytes(StandardCharsets.UTF_8).length>McpLimits.PACKET)result=McpService.error("response too large; request smaller page").toString();}
                catch(Exception e){result=McpService.error(e.getClass().getSimpleName()+": "+e.getMessage()).toString();}
                HdPanelMod.NETWORK.sendTo(new Reply(w.p.id,result),w.player);
                if(System.nanoTime()-start>2000000L)return;
            }
        }
    }
}
