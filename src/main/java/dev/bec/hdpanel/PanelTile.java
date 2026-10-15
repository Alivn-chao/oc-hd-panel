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

@cpw.mods.fml.common.Optional.InterfaceList({
    @cpw.mods.fml.common.Optional.Interface(iface="cofh.api.energy.IEnergyReceiver",modid="CoFHCore"),
    @cpw.mods.fml.common.Optional.Interface(iface="ic2.api.energy.tile.IEnergySink",modid="IC2"),
    @cpw.mods.fml.common.Optional.Interface(iface="appeng.api.networking.IGridHost",modid="appliedenergistics2")})
public class PanelTile extends TileEntityEnvironment implements cofh.api.energy.IEnergyReceiver,ic2.api.energy.tile.IEnergySink,appeng.api.networking.IGridHost {
    private final PanelPower battery=new PanelPower();
    public boolean screenEnabled=true,powered;
    public int brightness=100,rfStored,rfCapacity=PanelPower.capacity,rfPerTick=PanelPower.fullBrightnessCost;
    public double ocStored,ocCapacity;
    private boolean euRegistered;
    private Object aeGrid;
    private NBTTagCompound aeSaved=new NBTTagCompound();
    private boolean aeSecurityBlocked;
    private double ocPerRF(){return li.cil.oc.Settings.get().ratioRedstoneFlux();}
    private double rfPerEU(){return PanelPower.rfPerEU(li.cil.oc.Settings.get().ratioIndustrialCraft2(),ocPerRF());}
    private boolean acceptsPowerSide(net.minecraftforge.common.util.ForgeDirection from){return from!=null&&from!=net.minecraftforge.common.util.ForgeDirection.UNKNOWN&&from.ordinal()!=PanelFacing.front(getBlockMetadata());}
    private long lastSettings=-100;
    public boolean displayActive(){PanelTile owner=master();return owner.screenEnabled&&owner.powered;}
    public float displayBrightness(){return master().brightness/100F;}
    @Override @cpw.mods.fml.common.Optional.Method(modid="CoFHCore")
    public boolean canConnectEnergy(net.minecraftforge.common.util.ForgeDirection from){return acceptsPowerSide(from);}
    @Override @cpw.mods.fml.common.Optional.Method(modid="CoFHCore")
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from,int amount,boolean simulate){
        if(worldObj==null||worldObj.isRemote||!acceptsPowerSide(from))return 0;
        return receiveSharedRF(amount,simulate);
    }
    private int receiveSharedRF(int amount,boolean simulate){
        java.util.List<PanelTile> cells=master().wallCells();
        int accepted=PanelPower.receive(batteries(cells),amount,simulate);
        if(accepted>0&&!simulate)for(PanelTile cell:cells)cell.markDirty();return accepted;
    }
    @Override @cpw.mods.fml.common.Optional.Method(modid="CoFHCore")
    public int getEnergyStored(net.minecraftforge.common.util.ForgeDirection from){return worldObj==null?battery.stored():worldObj.isRemote?master().rfStored:PanelPower.total(batteries(master().wallCells()));}
    @Override @cpw.mods.fml.common.Optional.Method(modid="CoFHCore")
    public int getMaxEnergyStored(net.minecraftforge.common.util.ForgeDirection from){return master().wallWidth*master().wallHeight*PanelPower.capacity;}
    @Override @cpw.mods.fml.common.Optional.Method(modid="IC2")
    public boolean acceptsEnergyFrom(TileEntity emitter,net.minecraftforge.common.util.ForgeDirection side){return acceptsPowerSide(side);}
    @Override @cpw.mods.fml.common.Optional.Method(modid="IC2")
    public int getSinkTier(){return PanelPower.euSinkTier;}
    @Override @cpw.mods.fml.common.Optional.Method(modid="IC2")
    public double getDemandedEnergy(){double factor=rfPerEU();return worldObj==null||worldObj.isRemote||factor<=0?0:receiveSharedRF(Integer.MAX_VALUE,true)/factor;}
    @Override @cpw.mods.fml.common.Optional.Method(modid="IC2")
    public double injectEnergy(net.minecraftforge.common.util.ForgeDirection from,double amount,double voltage){
        if(worldObj==null||worldObj.isRemote||!acceptsPowerSide(from))return amount;
        double factor=rfPerEU();int rf=PanelPower.equivalentRF(amount,factor,PanelPower.maxReceive);
        return rf==0?amount:Math.max(0,amount-receiveSharedRF(rf,false)/factor);
    }
    @cpw.mods.fml.common.Optional.Method(modid="IC2")
    private void joinEU(){if(!euRegistered){net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ic2.api.energy.event.EnergyTileLoadEvent(this));euRegistered=true;}}
    @cpw.mods.fml.common.Optional.Method(modid="IC2")
    private void leaveEU(){if(euRegistered){net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ic2.api.energy.event.EnergyTileUnloadEvent(this));euRegistered=false;}}
    private void disconnectPower(){if(worldObj!=null&&!worldObj.isRemote){if(cpw.mods.fml.common.Loader.isModLoaded("IC2"))leaveEU();if(cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2"))leaveAE();}}
    @Override public void invalidate(){disconnectPower();super.invalidate();}
    @Override public void onChunkUnload(){disconnectPower();super.onChunkUnload();}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    private PanelAEGrid ae(){if(aeGrid==null)aeGrid=new PanelAEGrid(this,aeSaved);return (PanelAEGrid)aeGrid;}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    private void joinAE(){if(!aeSecurityBlocked)ae().ready();}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    private void leaveAE(){if(aeGrid!=null){((PanelAEGrid)aeGrid).save(aeSaved);((PanelAEGrid)aeGrid).destroy();aeGrid=null;}}
    @Override @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    public appeng.api.networking.IGridNode getGridNode(net.minecraftforge.common.util.ForgeDirection side){return worldObj==null||worldObj.isRemote||aeSecurityBlocked||(side!=net.minecraftforge.common.util.ForgeDirection.UNKNOWN&&!acceptsPowerSide(side))?null:ae().node();}
    @Override @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    public appeng.api.util.AECableType getCableConnectionType(net.minecraftforge.common.util.ForgeDirection side){return acceptsPowerSide(side)?appeng.api.util.AECableType.GLASS:appeng.api.util.AECableType.NONE;}
    @Override @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    public void securityBreak(){aeSecurityBlocked=true;leaveAE();markDirty();}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    public void aeOwner(EntityPlayer player){ae().owner(player);markDirty();}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    private void saveAE(){if(aeGrid!=null)((PanelAEGrid)aeGrid).save(aeSaved);}
    @cpw.mods.fml.common.Optional.Method(modid="appliedenergistics2")
    private void topUpAE(java.util.List<PanelTile> cells,int needed){
        int room=receiveSharedRF(needed,true);if(room<=0||needed<=0)return;
        double factor=PanelPower.rfPerEU(li.cil.oc.Settings.get().ratioAppliedEnergistics2(),ocPerRF());
        for(PanelTile cell:cells){if(cell.aeGrid==null||cell.aeSecurityBlocked)continue;int rf=((PanelAEGrid)cell.aeGrid).drawRF(room,factor);if(rf>0){receiveSharedRF(rf,false);return;}}
    }
    private static PanelPower[] batteries(java.util.List<PanelTile> cells){PanelPower[] result=new PanelPower[cells.size()];for(int i=0;i<result.length;i++)result[i]=cells.get(i).battery;return result;}
    private java.util.List<PanelTile> wallCells(){
        java.util.List<PanelTile> cells=new java.util.ArrayList<PanelTile>();
        int meta=getBlockMetadata(),dx=meta==0?1:meta==2?-1:0,dz=meta==1?-1:meta==3?1:0;
        for(int col=0;col<wallWidth;col++)for(int r=0;r<wallHeight;r++){
            int x=xCoord+dx*(col-column),y=yCoord+r-row,z=zCoord+dz*(col-column);
            if(!worldObj.blockExists(x,y,z))continue;
            TileEntity entity=worldObj.getTileEntity(x,y,z);
            if(entity instanceof PanelTile&&entity.getBlockMetadata()==meta&&((PanelTile)entity).master()==this)cells.add((PanelTile)entity);
        }return cells;
    }
    private void powerTick(long now){
        if(master()!=this)return;
        java.util.List<PanelTile> cells=wallCells();PanelPower[] batteries=batteries(cells);
        rfPerTick=PanelPower.cost(wallWidth*wallHeight,brightness);rfCapacity=wallWidth*wallHeight*PanelPower.capacity;
        final li.cil.oc.api.network.Connector connector=node instanceof li.cil.oc.api.network.Connector?(li.cil.oc.api.network.Connector)node:null;
        boolean next=screenEnabled&&cells.size()==wallWidth*wallHeight&&PanelPower.consumeMixed(batteries,rfPerTick,ocPerRF(),new PanelPower.ExternalPower(){
            public boolean tryConsume(double amount){return connector!=null&&connector.tryChangeBuffer(-amount);}
        });
        if(!next&&screenEnabled&&cells.size()==wallWidth*wallHeight&&cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2")){
            topUpAE(cells,Math.max(0,rfPerTick-PanelPower.total(batteries)));next=PanelPower.consume(batteries,rfPerTick);
        }
        if(next)for(PanelTile cell:cells)cell.markDirty();
        rfStored=PanelPower.total(batteries);
        ocStored=connector==null?0:connector.globalBuffer();ocCapacity=connector==null?0:connector.globalBufferSize();
        if(next!=powered){powered=next;dirty=true;worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);if(!next)finish(false,"屏幕已关闭或RF不足，请核对操作结果");}
        if(now%20==0)dirty=true;
    }
    public void settings(EntityPlayerMP player,boolean enabled,int level){
        PanelTile owner=master();if(owner!=this){owner.settings(player,enabled,level);return;}
        long now=worldObj.getTotalWorldTime();
        if(worldObj.isRemote||distanceSq(player.posX,player.posY,player.posZ)>64||now-lastSettings<2
            ||!worldObj.canMineBlock(player,xCoord,yCoord,zCoord)||!player.canPlayerEdit(xCoord,yCoord,zCoord,1,player.getHeldItem())
            ||(controller!=null&&!controller.canInteract(player.getCommandSenderName())))return;
        lastSettings=now;
        if(!enabled)finish(false,"屏幕已关闭，请核对操作结果");
        for(PanelTile cell:wallCells()){cell.screenEnabled=enabled;cell.brightness=PanelPower.brightness(level);if(!enabled)cell.powered=false;cell.markDirty();worldObj.markBlockForUpdate(cell.xCoord,cell.yCoord,cell.zCoord);}
    }
    public String payload = "{}";
    public long revision;
    public boolean online;
    public String app="welcome",layout=AppModel.WELCOME,values="{}";
    public String webPackage="";
    private final java.util.Map<String,Long> webRequests=new java.util.HashMap<String,Long>();
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
            screenEnabled=owner.screenEnabled;brightness=owner.brightness;powered=false;
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
    @Callback(doc="setWebApp(package:table):boolean -- Upload a bounded HTML/CSS/JS page package; does not execute machine actions.")
    public Object[] setWebApp(Context context,Arguments args){
        if(worldObj.getTotalWorldTime()-lastUpdate<10)return new Object[]{false,"rate limited"};
        String source;try{source=WebAppModel.pack(args.checkTable(0));}catch(IllegalArgumentException e){return new Object[]{null,e.getMessage()};}
        if(!claim(context))return new Object[]{null,"panel bound to another computer or is a wall follower"};
        finish(false,"页面已更换，旧请求未确认");webPackage=source;layout=WebAppModel.descriptor(source);values="{}";payload="{}";app="web";touch();return new Object[]{true};
    }
    @Callback(doc="publish(data:table):boolean -- Publish a complete web-app snapshot, at most twice per second.")
    public Object[] publish(Context context,Arguments args){
        if(!"web".equals(app)||!claim(context))return new Object[]{null,"call setWebApp first"};
        long now=worldObj.getTotalWorldTime();if(now-lastUpdate<10)return new Object[]{false,"rate limited"};
        String next;try{next=WebAppModel.data(args.checkTable(0));}catch(IllegalArgumentException e){return new Object[]{null,e.getMessage()};}
        if(!next.equals(values)){values=next;revision++;dirty=true;markDirty();}lastUpdate=now;if(!online){online=true;dirty=true;}return new Object[]{true};
    }
    public void sendWebPackage(EntityPlayerMP player,String hash){
        if(!"web".equals(app)||webPackage.isEmpty()||distanceSq(player.posX,player.posY,player.posZ)>1024||!WebAppModel.hash(webPackage).equals(hash))return;
        long now=worldObj.getTotalWorldTime();String id=player.getUniqueID().toString();Long last=webRequests.get(id);
        if(last!=null&&now-last<40)return;if(webRequests.size()>128)webRequests.clear();webRequests.put(id,now);
        byte[] bytes=webPackage.getBytes(java.nio.charset.StandardCharsets.UTF_8);int total=(bytes.length+WebAppModel.CHUNK-1)/WebAppModel.CHUNK;
        for(int i=0;i<total;i++){int start=i*WebAppModel.CHUNK,end=Math.min(bytes.length,start+WebAppModel.CHUNK);
            HdPanelMod.NETWORK.sendTo(new WebAssetPacket(hash,i,total,java.util.Arrays.copyOfRange(bytes,start,end)),player);}
    }
    @Callback(doc="getWallSize():number,number -- Number of physical panels in this wall.")
    public Object[] getWallSize(Context context,Arguments args){return new Object[]{wallWidth,wallHeight};}

    @Callback(doc="clearUI():boolean -- Return to the welcome page and release this computer's binding.")
    public Object[] clearUI(Context context,Arguments args) {
        if(master()!=this||(bound()&&!controllerAddress.equals(context.node().address())))return new Object[]{false};
        finish(false,"页面已清空，操作未确认");
        app="welcome";layout=AppModel.WELCOME;values="{}";payload="{}";webPackage="";
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
        node = Network.newNode(this, Visibility.Network).withComponent("hd_panel", Visibility.Network).withConnector(0).create();
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
        if(cpw.mods.fml.common.Loader.isModLoaded("IC2"))joinEU();
        if(cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2"))joinAE();
        long now = worldObj.getTotalWorldTime();
        if(now-wallChecked>=20)WallAssembly.rebuild(this,now);
        powerTick(now);
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
        if (worldObj.isRemote || action < 0 || action > 8 || request<=0 || value<0 || master()!=this) return;
        if(!displayActive()){reply(player,request,2,"屏幕已关闭或RF不足");return;}
        if (!online || controller==null) { reply(player, request, 2, "面板离线，无法提交"); return; }
        if (distanceSq(player.posX,player.posY,player.posZ) > 64
                || !controller.canInteract(player.getCommandSenderName())) {
            reply(player, request, 2, "距离过远或没有此 OC 电脑的操作权限"); return;
        }
        if(action==5&&(!"custom".equals(app)||!AppModel.actionable(layout,values,widget,value))) {reply(player,request,2,"控件已禁用或不存在");return;}
        if(action<5&&!"bec_dashboard".equals(app)){reply(player,request,2,"当前应用不接受此操作");return;}
        if((action==6||action==7)&&(!"bec_dashboard".equals(app)||!BindingModel.accepts(payload,widget,action==7))) {
            reply(player,request,2,"绑定字段无效或当前不允许保存");return;
        }
        if(action==8&&(!"web".equals(app)||!WebAppModel.event(layout,widget))){reply(player,request,2,"页面版本或事件无效");return;}
        long now = worldObj.getTotalWorldTime();
        if (pendingRequest!=0 || now - lastClick < 5) { reply(player, request, 2, "面板正在处理请求，请稍后再试"); return; }
        lastClick = now;
        pendingRequest=request; pendingPlayer=player; pendingSince=now;
        if(action==8){
            com.google.gson.JsonObject e=new com.google.gson.JsonParser().parse(widget).getAsJsonObject();
            if(!controller.signal("hd_panel_web",node.address(),e.get("event").getAsString(),player.getCommandSenderName(),request,e.get("args").toString()))finish(false,"OC事件队列未接受请求");
            else reply(player,request,0,"请求已送达OC");return;
        }
        String[] actions = {"refresh", "pause", "resume", "patterns", "cache_target","", "binding_save", "binding_draft"};
        if (!controller.signal(action==5?"hd_panel_event":"hd_panel_action", node.address(), action==5?widget:actions[action], player.getCommandSenderName(), request, value,action>=6?widget:""))
            finish(false, "OC 事件队列未接受请求");
        else reply(player, request, 0, "请求已送达 OC，等待处理回执…");
    }
    private NBTTagCompound snapshot() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("data", payload); nbt.setLong("revision", revision); nbt.setBoolean("online", online);
        nbt.setString("app",app);nbt.setString("layout",layout);nbt.setString("values",values);
        nbt.setBoolean("screenEnabled",screenEnabled);nbt.setBoolean("powered",powered);nbt.setInteger("brightness",brightness);
        nbt.setInteger("rfStored",rfStored);nbt.setInteger("rfCapacity",rfCapacity);nbt.setInteger("rfPerTick",rfPerTick);
        nbt.setDouble("ocStored",ocStored);nbt.setDouble("ocCapacity",ocCapacity);
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
        byte[] web=nbt.getByteArray("webPackage");webPackage=web.length<=WebAppModel.MAX_PACKAGE?new String(web,java.nio.charset.StandardCharsets.UTF_8):"";
        readDisplay(nbt);
        battery.restore(nbt.getInteger("localRF"));powered=false;
        aeSaved=(NBTTagCompound)nbt.getCompoundTag("aePowerNode").copy();aeSecurityBlocked=nbt.getBoolean("aeSecurityBlocked");
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        if(cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2"))saveAE();
        nbt.setTag("aePowerNode",aeSaved);nbt.setBoolean("aeSecurityBlocked",aeSecurityBlocked);
        nbt.setInteger("localRF",battery.stored());
        super.writeToNBT(nbt); nbt.setString("data", payload); nbt.setLong("revision", revision);
        nbt.setString("controller", controllerAddress);
        nbt.setByteArray("webPackage",webPackage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        NBTTagCompound display=snapshot();for(Object key:display.func_150296_c()) {String k=(String)key;nbt.setTag(k,display.getTag(k));}
    }
    private void readDisplay(NBTTagCompound n) {
        screenEnabled=!n.hasKey("screenEnabled")||n.getBoolean("screenEnabled");powered=n.getBoolean("powered");
        brightness=n.hasKey("brightness")?PanelPower.brightness(n.getInteger("brightness")):100;
        rfStored=Math.max(0,n.getInteger("rfStored"));rfCapacity=Math.max(0,n.getInteger("rfCapacity"));rfPerTick=Math.max(0,n.getInteger("rfPerTick"));
        ocStored=Math.max(0,n.getDouble("ocStored"));ocCapacity=Math.max(0,n.getDouble("ocCapacity"));
        app=n.hasKey("app")?n.getString("app"):"welcome";layout=n.hasKey("layout")?n.getString("layout"):AppModel.WELCOME;values=n.hasKey("values")?n.getString("values"):"{}";
        wallWidth=Math.max(1,Math.min(8,n.getInteger("wallWidth")));wallHeight=Math.max(1,Math.min(6,n.getInteger("wallHeight")));
        column=Math.max(0,Math.min(wallWidth-1,n.getInteger("column")));row=Math.max(0,Math.min(wallHeight-1,n.getInteger("row")));masterX=n.getInteger("masterX");masterY=n.getInteger("masterY");masterZ=n.getInteger("masterZ");wallConfigured=n.hasKey("masterX");
    }
    @Override public double getMaxRenderDistanceSquared() { return 32 * 32; }
}
