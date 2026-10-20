package dev.bec.hdpanel.mcp;

import com.google.gson.*;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.*;
import li.cil.oc.api.fs.*;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class McpService {
    private static final Gson JSON = new Gson();
    public static JsonObject error(String message) { JsonObject r=new JsonObject();r.addProperty("error",message);return r; }
    public static String text(JsonObject r,String key) { return r.has(key)?r.get(key).getAsString():""; }
    public static JsonElement handle(EntityPlayerMP player, JsonObject request) throws Exception {
        String op=text(request,"op");
        if(op.equals("list")) {
            JsonArray out=new JsonArray();
            { for(McpSession card:McpBox.computers(player)) if(allowed(player,card)) {out.add(info(card));if(out.size()>=64)break;} }
            return out;
        }
        McpSession card=null;
        { for(McpSession c:McpBox.computers(player)) if(text(request,"computer").equals(c.id()) && allowed(player,c)) {card=c;break;} }
        if(card==null)throw new IllegalArgumentException("computer unavailable or unauthorized (case owner, OC users, dimension, loaded chunk)");
        Machine m=card.host.machine();
        if(op.equals("status"))return info(card);
        if(op.equals("start") || op.equals("stop")) { JsonObject out=new JsonObject();out.addProperty("changed",op.equals("start")?m.start():m.stop());return out; }
        if(op.equals("components")) {
            JsonArray out=new JsonArray();int visited=0, visible=0;
            int offset=request.has("offset")?request.get("offset").getAsInt():0;
            if(offset<0||offset>4096)throw new IllegalArgumentException("invalid component offset");
            for(Node node:m.node().reachableNodes()) {
                if(++visited>4096)break;
                if(!(node instanceof Component) || !((Component)node).canBeSeenFrom(m.node()))continue;
                if(visible++<offset)continue;
                Component c=(Component)node;JsonObject entry=new JsonObject();entry.addProperty("address",node.address());entry.addProperty("type",c.name());entry.add("methods",JSON.toJsonTree(c.methods()));
                if(node.host() instanceof li.cil.oc.server.component.FileSystem) {
                    FileSystem fs=((li.cil.oc.server.component.FileSystem)node.host()).fileSystem();
                    entry.addProperty("readOnly",fs.isReadOnly());entry.addProperty("capacity",fs.spaceTotal());entry.addProperty("used",fs.spaceUsed());entry.addProperty("openOS",fs.exists("boot/94_shell.lua"));
                }
                out.add(entry);if(out.size()>=32)break;
            }
            JsonObject page=new JsonObject();page.add("components",out);page.addProperty("nextOffset",offset+out.size());page.addProperty("mayHaveMore",out.size()==32);return page;
        }
        if(op.equals("install_agent"))return install(card,text(request,"filesystem"));
        if(op.equals("job"))return card.job(text(request,"job"));
        if(!Arrays.asList("files","read","write","run","runtime","invoke").contains(op))throw new IllegalArgumentException("unknown operation");
        String id=text(request,"job");if(!id.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("job id required");
        JsonObject args=request.has("args")?request.getAsJsonObject("args"):new JsonObject();
        return card.submit(id,op,args);
    }
    private static boolean allowed(EntityPlayerMP p,McpSession c) {
        return p!=null && c.owner.equals(p.getUniqueID().toString()) && c.box.connected(c.host.machine()) && c.host.world()==p.worldObj
            && c.host.world().blockExists((int)c.host.xPosition(),(int)c.host.yPosition(),(int)c.host.zPosition())
            && c.host.machine()!=null && c.host.machine().canInteract(p.getCommandSenderName());
    }
    private static JsonObject info(McpSession c) {
        Machine m=c.host.machine();JsonObject r=new JsonObject();
        r.addProperty("computer",c.id());r.addProperty("machine",m.node().address());r.addProperty("running",m.isRunning());r.addProperty("paused",m.isPaused());r.addProperty("agentOnline",c.online());
        r.addProperty("uptime",m.upTime());r.addProperty("cpuSeconds",m.cpuTime());r.addProperty("lastError",m.lastError());
        r.addProperty("dimension",c.host.world().provider.dimensionId);r.addProperty("x",c.host.xPosition());r.addProperty("y",c.host.yPosition());r.addProperty("z",c.host.zPosition());return r;
    }
    private static JsonObject install(McpSession card,String address) throws Exception {
        if(card.host.machine().isRunning())throw new IllegalStateException("stop computer before installing agent; no live disk edits");
        Node node=card.host.machine().node().network().node(address);
        if(!(node instanceof Component) || !((Component)node).canBeSeenFrom(card.host.machine().node()) || !(node.host() instanceof li.cil.oc.server.component.FileSystem))throw new IllegalArgumentException("select a reachable OpenOS filesystem address from components");
        FileSystem fs=((li.cil.oc.server.component.FileSystem)node.host()).fileSystem();
        if(fs.isReadOnly() || !fs.exists("init.lua") || !fs.exists("lib/thread.lua") || !fs.exists("boot/94_shell.lua"))throw new IllegalArgumentException("target must be a writable OpenOS boot disk");
        byte[] bytes;
        try(InputStream in=McpService.class.getResourceAsStream("/assets/ochdpanel/mcp/99_ochd_mcp.lua")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();
        }
        String target="boot/99_ochd_mcp.lua", backup=".ochd-mcp/agent-backup.lua", temp=".ochd-mcp/agent-stage.lua";
        if(!fs.exists(".ochd-mcp") && !fs.makeDirectory(".ochd-mcp"))throw new IOException("cannot create agent staging directory");
        if(fs.exists(temp))throw new IllegalStateException("previous install staging file exists; inspect disk before retry");
        if(fs.exists(target) && fs.exists(backup))throw new IllegalStateException("existing agent backup present; inspect before upgrading");
        Handle h=fs.getHandle(fs.open(temp,Mode.Write));try{h.write(bytes);}finally{h.close();}
        boolean backed=false;
        try {
            if(fs.exists(target)) {if(!fs.rename(target,backup))throw new IOException("backup failed");backed=true;}
            if(!fs.rename(temp,target))throw new IOException("install rename failed");
        }catch(Exception ex){if(backed)fs.rename(backup,target);throw ex;}
        card.host.markChanged();JsonObject result=new JsonObject();result.addProperty("installed",true);result.addProperty("next","start computer");return result;
    }
}
