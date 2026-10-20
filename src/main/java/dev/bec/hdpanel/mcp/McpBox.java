package dev.bec.hdpanel.mcp;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

public final class McpBox extends TileEntityEnvironment {
    private static final Set<McpBox> BOXES=Collections.newSetFromMap(new WeakHashMap<McpBox,Boolean>());
    private final Map<Machine,McpSession> sessions=new WeakHashMap<Machine,McpSession>();
    volatile String owner="";
    volatile boolean enabled=true; private boolean registered;
    public McpBox(){node=Network.newNode(this,Visibility.Network).withComponent("ochd_mcp",Visibility.Network).create();}
    @Override public void updateEntity(){super.updateEntity();if(!registered&&worldObj!=null&&!worldObj.isRemote)synchronized(BOXES){BOXES.add(this);registered=true;}}
    @Override public void invalidate(){synchronized(BOXES){BOXES.remove(this);registered=false;}synchronized(sessions){sessions.clear();}super.invalidate();}
    @Override public void onChunkUnload(){synchronized(BOXES){BOXES.remove(this);registered=false;}synchronized(sessions){sessions.clear();}super.onChunkUnload();}
    boolean connected(Machine machine){return !isInvalid()&&enabled&&!owner.isEmpty()&&node!=null&&node.network()!=null&&machine!=null&&machine.node().network()==node.network()&&((Component)node).canBeSeenFrom(machine.node());}
    private McpSession session(Machine machine){synchronized(sessions){McpSession c=sessions.get(machine);if(c==null){if(sessions.size()>=64)throw new IllegalStateException("MCP case computer limit (64)");c=new McpSession(machine.host(),this);sessions.put(machine,c);}return c;}}
    static List<McpSession> computers(net.minecraft.entity.player.EntityPlayerMP player){
        List<McpSession> result=new ArrayList<McpSession>();
        synchronized(BOXES){for(McpBox box:BOXES){if(!box.enabled || !box.owner.equals(player.getUniqueID().toString()) || box.worldObj!=player.worldObj || box.node==null||box.node.network()==null)continue;int count=0;
            synchronized(box.sessions){box.sessions.keySet().removeIf(m -> !box.connected(m));}
            for(Node n:box.node.reachableNodes()){if(n.host() instanceof Machine && box.connected((Machine)n.host()))result.add(box.session((Machine)n.host()));if(++count>=512||result.size()>=64)break;}
            if(result.size()>=64)break;
        }}return result;
    }
    private McpSession from(Context context){if(!(context instanceof Machine)||!connected((Machine)context))throw new IllegalArgumentException("computer is not connected to this MCP case");return session((Machine)context);}
    @Callback(direct=true,limit=4) public Object[] next(Context c,Arguments a){return from(c).next(c,a);}
    @Callback(direct=true,limit=4) public Object[] complete(Context c,Arguments a){return from(c).complete(c,a);}
    void toggle(){enabled=!enabled;synchronized(sessions){sessions.clear();}markDirty();}
    void owner(String value){owner=value;synchronized(sessions){sessions.clear();}markDirty();}
    @Override public void readFromNBT(NBTTagCompound nbt){super.readFromNBT(nbt);owner=nbt.getString("ochdOwner");enabled=!nbt.getBoolean("ochdDisabled");}
    @Override public void writeToNBT(NBTTagCompound nbt){super.writeToNBT(nbt);nbt.setString("ochdOwner",owner);nbt.setBoolean("ochdDisabled",!enabled);}
}
