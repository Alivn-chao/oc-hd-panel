package dev.bec.hdpanel;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.network.Component;
import com.google.gson.JsonObject;
import net.minecraft.tileentity.TileEntity;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

public class PanelTile extends TileEntityEnvironment {
    public String payload = "{}";
    public long revision;
    public boolean online;
    public String app="welcome",layout=AppModel.WELCOME,values="{}";
    public int wallWidth=1,wallHeight=1,column,row;
    private int masterX,masterY,masterZ;
    private boolean wallConfigured;
    long wallChecked=-1000;
    private long lastUpdate = -1000;
    private boolean dirty;
    private Context controller;
    private String controllerAddress = "";
    private long lastClick = -1000;
    private int pendingRequest;
    private EntityPlayerMP pendingPlayer;
    private long pendingSince;

    boolean bound() { return !controllerAddress.isEmpty(); }
    public PanelTile master() {
        if(!wallConfigured||(masterX==xCoord&&masterY==yCoord&&masterZ==zCoord))return this;
        if(worldObj!=null&&worldObj.blockExists(masterX,masterY,masterZ)) {
            TileEntity t=worldObj.getTileEntity(masterX,masterY,masterZ);if(t instanceof PanelTile&&!t.isInvalid())return (PanelTile)t;
        }return this;
    }
    void configureWall(PanelTile owner,int width,int height,int col,int r) {
        if(!wallConfigured||masterX!=owner.xCoord||masterY!=owner.yCoord||masterZ!=owner.zCoord||wallWidth!=width||wallHeight!=height||column!=col||row!=r) {
            masterX=owner.xCoord;masterY=owner.yCoord;masterZ=owner.zCoord;wallWidth=width;wallHeight=height;column=col;row=r;wallConfigured=true;dirty=true;markDirty();
        }
        if(node instanceof Component)((Component)node).setVisibility(owner==this?Visibility.Network:Visibility.None);
    }
    public double distanceSq(double x,double y,double z) {
        int meta=getBlockMetadata();int dx=meta==0?1:meta==2?-1:0,dz=meta==1?-1:meta==3?1:0;
        double ox=xCoord+.5-dx*column,oy=yCoord-row,oz=zCoord+.5-dz*column;
        double along=(x-ox)*dx+(z-oz)*dz;
        double closest=Math.max(-.5,Math.min(wallWidth-.5,along));
        double cx=ox+dx*closest,cz=oz+dz*closest,cy=Math.max(oy,Math.min(oy+wallHeight,y));
        return (x-cx)*(x-cx)+(y-cy)*(y-cy)+(z-cz)*(z-cz);
    }
    private boolean claim(Context context) {
        if(master()!=this)return false;
        String a=context.node().address();if(bound()&&!controllerAddress.equals(a))return false;
        controller=context;controllerAddress=a;markDirty();return true;
    }
    private void touch() { lastUpdate=worldObj.getTotalWorldTime();online=true;dirty=true;revision++;markDirty(); }
    @Callback(doc="setUI(layout:table):boolean -- Set up to 4 custom pages and 32 widgets; binds this computer.")
    public Object[] setUI(Context context,Arguments args) {
        if(worldObj.getTotalWorldTime()-lastUpdate<10)return new Object[]{false,"rate limited"};
        String normalized;
        try{normalized=AppModel.layout(args.checkTable(0));}catch(IllegalArgumentException e){return new Object[]{null,e.getMessage()};}
        if(!claim(context))return new Object[]{null,"panel bound to another computer or is a wall follower"};
        finish(false,"页面已重新定义，旧操作未确认");
        app="custom";layout=normalized;values="{}";touch();return new Object[]{true};
    }
    @Callback(doc="updateUI(values:table):boolean -- Publish values keyed by widget id; at most twice per second.")
    public Object[] updateUI(Context context,Arguments args) {
        if(!"custom".equals(app)||!claim(context))return new Object[]{null,"call setUI on the wall component first"};
        long now=worldObj.getTotalWorldTime();if(now-lastUpdate<10)return new Object[]{false,"rate limited"};
        String normalized=AppModel.values(layout,args.checkTable(0));
        if(!values.equals(normalized)) {values=normalized;revision++;dirty=true;markDirty();}
        lastUpdate=now;if(!online){online=true;dirty=true;}return new Object[]{true};
    }
    @Callback(doc="getWallSize():number,number -- Number of physical panels in this wall.")
    public Object[] getWallSize(Context context,Arguments args){return new Object[]{wallWidth,wallHeight};}

    @Callback(doc="clearUI():boolean -- Return to the welcome page and release this computer's binding.")
    public Object[] clearUI(Context context,Arguments args) {
        if(master()!=this||(bound()&&!controllerAddress.equals(context.node().address())))return new Object[]{false};
        finish(false,"页面已清空，操作未确认");
        app="welcome";layout=AppModel.WELCOME;values="{}";payload="{}";
        controller=null;controllerAddress="";online=false;revision++;dirty=true;markDirty();
        return new Object[]{true};
    }

    private void reply(EntityPlayerMP player, int request, int state, String message) {
        if (player != null && player.playerNetServerHandler != null && player.playerNetServerHandler.netManager.isChannelOpen())
            HdPanelMod.NETWORK.sendTo(new ActionReplyPacket(this, request, state, message), player);
    }
    private void finish(boolean success, String message) {
        if (pendingRequest == 0) return;
        reply(pendingPlayer, pendingRequest, success ? 1 : 2, message);
        pendingRequest=0; pendingPlayer=null;
    }
    @Callback(doc = "ack(request:number,success:boolean,message:string):boolean -- Reply to a pending UI request.")
    public Object[] ack(Context context, Arguments args) {
        int request=args.checkInteger(0);
        if (controller == null || !controllerAddress.equals(context.node().address()) || request!=pendingRequest || request<=0)
            return new Object[] {false};
        finish(args.checkBoolean(1), PanelModel.text(args.checkString(2), 128));
        return new Object[] {true};
    }

    public PanelTile() {
        node = Network.newNode(this, Visibility.Network).withComponent("hd_panel", Visibility.Network).create();
    }
    @Callback(doc = "setPage(name:string):boolean -- Select bec_dashboard.")
    public Object[] setPage(Context context, Arguments args) {
        if (!"bec_dashboard".equals(args.checkString(0))) return new Object[] {null, "unsupported page"};
        if(!claim(context))return new Object[]{null,"panel bound to another computer or is a wall follower"};
        if(!"bec_dashboard".equals(app)){app="bec_dashboard";touch();}
        return new Object[] {true};
    }
    @Callback(doc = "update(data:table):boolean -- Publish a bounded BEC snapshot, at most twice per second.")
    public Object[] update(Context context, Arguments args) {
        String address = context.node().address();
        if(master()!=this)return new Object[]{null,"use the wall master component"};
        if (!controllerAddress.isEmpty() && !controllerAddress.equals(address))
            return new Object[] {null, "panel bound to another computer"};
        long now = worldObj.getTotalWorldTime();
        if (now - lastUpdate < 10) return new Object[] {false, "rate limited"};
        String json = PanelModel.normalize(args.checkTable(0));
        if(!"bec_dashboard".equals(app)) {app="bec_dashboard";dirty=true;revision++;}
        if (!payload.equals(json)) { payload = json; revision++; dirty = true; markDirty(); }
        controller = context;
        controllerAddress = address;
        lastUpdate = now;
        if (!online) { online = true; dirty = true; }
        return new Object[] {true};
    }
    @Callback(doc = "getResolution():number,number -- Native pixel resolution.")
    public Object[] getResolution(Context context, Arguments args) { return new Object[] {1920, 1080}; }
    @Callback(doc="getUIResolution():number,number -- Logical Lua layout coordinates, independent of the 1080p raster.")
    public Object[] getUIResolution(Context context,Arguments args){return new Object[]{1280,720};}
    @Callback(doc = "release():boolean -- Unbind this computer from the panel.")
    public Object[] release(Context context, Arguments args) {
        if (!controllerAddress.equals(context.node().address())) return new Object[] {false};
        finish(false, "OC 连接已退出，操作未确认");
        controller = null; controllerAddress = ""; online = false; dirty = true; markDirty();
        return new Object[] {true};
    }
    @Override public void updateEntity() {
        if (worldObj.isRemote) return;
        super.updateEntity();
        long now = worldObj.getTotalWorldTime();
        if(now-wallChecked>=20)WallAssembly.rebuild(this,now);
        if (pendingRequest != 0 && now - pendingSince > 160) finish(false, "未收到 OC 回执；请核对状态后重试");
        if (online && (now - lastUpdate > 100 || controller == null || !controller.isRunning())) {
            online = false; dirty = true;
            finish(false, "OC 连接中断，操作未确认");
        }
        if (dirty && now % 10 == 0) {
            dirty = false;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
    public void action(EntityPlayerMP player, int action, int request, int value,String widget) {
        if (worldObj.isRemote || action < 0 || action > 7 || request<=0 || value<0 || master()!=this) return;
        if (!online || controller==null) { reply(player, request, 2, "面板离线，无法提交"); return; }
        if (distanceSq(player.posX,player.posY,player.posZ) > 64
                || !controller.canInteract(player.getCommandSenderName())) {
            reply(player, request, 2, "距离过远或没有此 OC 电脑的操作权限"); return;
        }
        if(action==5&&(!"custom".equals(app)||!AppModel.actionable(layout,values,widget,value))) {reply(player,request,2,"控件已禁用或不存在");return;}
        if(action<5&&!"bec_dashboard".equals(app)){reply(player,request,2,"当前应用不接受此操作");return;}
        if(action>=6&&(!"bec_dashboard".equals(app)||!BindingModel.accepts(payload,widget,action==7))) {
            reply(player,request,2,"绑定字段无效或当前不允许保存");return;
        }
        long now = worldObj.getTotalWorldTime();
        if (pendingRequest!=0 || now - lastClick < 5) { reply(player, request, 2, "面板正在处理请求，请稍后再试"); return; }
        lastClick = now;
        pendingRequest=request; pendingPlayer=player; pendingSince=now;
        String[] actions = {"refresh", "pause", "resume", "patterns", "cache_target","", "binding_save", "binding_draft"};
        if (!controller.signal(action==5?"hd_panel_event":"hd_panel_action", node.address(), action==5?widget:actions[action], player.getCommandSenderName(), request, value,action>=6?widget:""))
            finish(false, "OC 事件队列未接受请求");
        else reply(player, request, 0, "请求已送达 OC，等待处理回执…");
    }
    private NBTTagCompound snapshot() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("data", payload); nbt.setLong("revision", revision); nbt.setBoolean("online", online);
        nbt.setString("app",app);nbt.setString("layout",layout);nbt.setString("values",values);
        nbt.setInteger("wallWidth",wallWidth);nbt.setInteger("wallHeight",wallHeight);nbt.setInteger("column",column);nbt.setInteger("row",row);
        nbt.setInteger("masterX",wallConfigured?masterX:xCoord);nbt.setInteger("masterY",wallConfigured?masterY:yCoord);nbt.setInteger("masterZ",wallConfigured?masterZ:zCoord);
        return nbt;
    }
    @Override public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, snapshot());
    }
    @Override public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        int oldWidth=wallWidth,oldHeight=wallHeight,oldColumn=column,oldRow=row;
        NBTTagCompound nbt = packet.func_148857_g();
        payload = nbt.getString("data"); revision = nbt.getLong("revision"); online = nbt.getBoolean("online");
        readDisplay(nbt);
        if(worldObj!=null&&(oldWidth!=wallWidth||oldHeight!=wallHeight||oldColumn!=column||oldRow!=row))
            worldObj.markBlockRangeForRenderUpdate(xCoord,yCoord,zCoord,xCoord,yCoord,zCoord);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt); payload = nbt.getString("data"); revision = nbt.getLong("revision");
        controllerAddress = nbt.getString("controller"); online = false;
        readDisplay(nbt);
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt); nbt.setString("data", payload); nbt.setLong("revision", revision);
        nbt.setString("controller", controllerAddress);
        NBTTagCompound display=snapshot();for(Object key:display.func_150296_c()) {String k=(String)key;nbt.setTag(k,display.getTag(k));}
    }
    private void readDisplay(NBTTagCompound n) {
        app=n.hasKey("app")?n.getString("app"):"welcome";layout=n.hasKey("layout")?n.getString("layout"):AppModel.WELCOME;values=n.hasKey("values")?n.getString("values"):"{}";
        wallWidth=Math.max(1,Math.min(8,n.getInteger("wallWidth")));wallHeight=Math.max(1,Math.min(6,n.getInteger("wallHeight")));
        column=Math.max(0,Math.min(wallWidth-1,n.getInteger("column")));row=Math.max(0,Math.min(wallHeight-1,n.getInteger("row")));masterX=n.getInteger("masterX");masterY=n.getInteger("masterY");masterZ=n.getInteger("masterZ");wallConfigured=n.hasKey("masterX");
    }
    @Override public double getMaxRenderDistanceSquared() { return 32 * 32; }
}
