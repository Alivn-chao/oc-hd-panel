package dev.bec.hdpanel.client;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import dev.bec.hdpanel.HdPanelMod;
import dev.bec.hdpanel.mcp.*;
import net.minecraft.client.Minecraft;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTP workers never access worlds or the Forge connection. Client tick owns dispatch. */
public final class McpClientBridge {
    private static final ArrayBlockingQueue<Pending> OUT=new ArrayBlockingQueue<Pending>(8);
    private static final ConcurrentHashMap<Integer,Pending> IN=new ConcurrentHashMap<Integer,Pending>();
    private static final AtomicInteger IDS=new AtomicInteger();
    private static volatile String session="";
    private static final class Pending {
        final int id=IDS.incrementAndGet();final String body;final String connection;final CompletableFuture<String> result=new CompletableFuture<String>();
        Pending(String body){this.body=body;this.connection=session;}
    }
    public static void start() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
            byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);final String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            JsonObject cfg=new JsonObject();cfg.addProperty("url","http://127.0.0.1:"+server.getAddress().getPort()+"/rpc");cfg.addProperty("token",token);
            Path path=Minecraft.getMinecraft().mcDataDir.toPath().resolve("config/ochd-mcp-client.json");Files.createDirectories(path.getParent());
            Files.write(path,cfg.toString().getBytes(StandardCharsets.UTF_8));
            server.createContext("/rpc",exchange -> {
                int code=200;String result;
                try {
                    if(!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/rpc"))throw new SecurityException("POST /rpc required");
                    if(exchange.getRequestHeaders().containsKey("Origin") || !java.security.MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")).getBytes(StandardCharsets.UTF_8)))throw new SecurityException("unauthorized");
                    String host=exchange.getRequestHeaders().getFirst("Host");if(!("127.0.0.1:"+server.getAddress().getPort()).equals(host))throw new SecurityException("invalid host");
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
                    while((n=exchange.getRequestBody().read(buf))!=-1){if(out.size()+n>65536)throw new IllegalArgumentException("request too large");out.write(buf,0,n);}
                    if(session.isEmpty())throw new IllegalStateException("join a Minecraft server first");
                    Pending pending=new Pending(new String(out.toByteArray(),StandardCharsets.UTF_8));
                    if(!OUT.offer(pending))throw new IllegalStateException("client bridge busy");
                    try{result=pending.result.get(12,TimeUnit.SECONDS);}finally{OUT.remove(pending);IN.remove(pending.id);}
                }catch(SecurityException ex){code=403;result=McpService.error(ex.getMessage()).toString();}
                catch(Exception ex){code=503;result=McpService.error("bridge request failed: "+ex.getClass().getSimpleName()+"; check connection, never blindly repeat a write").toString();}
                byte[] bytes=result.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(code,bytes.length);
                try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
            });
            server.setExecutor(new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),r->{Thread t=new Thread(r,"OCHD MCP HTTP");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()));
            server.start();
        }catch(Exception ex){System.err.println("[OCHD MCP] Could not start localhost bridge: "+ex);}
    }
    public static void tick() {
        Minecraft mc=Minecraft.getMinecraft();String next=mc.theWorld==null || mc.thePlayer==null || mc.getNetHandler()==null ? "" : Integer.toHexString(System.identityHashCode(mc.getNetHandler()));
        if(!session.equals(next)) {
            session=next;
            for(Pending p:IN.values())p.result.completeExceptionally(new IOException("game connection changed"));IN.clear();
            Pending p;while((p=OUT.poll())!=null)p.result.completeExceptionally(new IOException("game connection changed"));
        }
        for(int i=0;i<2;i++){Pending p=OUT.poll();if(p==null)break;if(!p.connection.equals(session)||session.isEmpty()){p.result.completeExceptionally(new IOException("disconnected"));continue;}IN.put(p.id,p);try{HdPanelMod.NETWORK.sendToServer(new McpPacket(p.id,p.body));}catch(Exception ex){IN.remove(p.id);p.result.completeExceptionally(ex);}}
    }
    public static void reply(McpPacket.Reply reply){Pending p=IN.remove(reply.id);if(p!=null)p.result.complete(reply.json);}
}
