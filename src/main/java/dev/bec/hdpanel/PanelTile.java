package dev.bec.hdpanel;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
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
    private long lastUpdate = -1000;
    private boolean dirty;
    private Context controller;
    private String controllerAddress = "";
    private long lastClick = -1000;
    private int pendingRequest;
    private EntityPlayerMP pendingPlayer;
    private long pendingSince;

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
        return new Object[] {true};
    }
    @Callback(doc = "update(data:table):boolean -- Publish a bounded BEC snapshot, at most twice per second.")
    public Object[] update(Context context, Arguments args) {
        String address = context.node().address();
        if (!controllerAddress.isEmpty() && !controllerAddress.equals(address))
            return new Object[] {null, "panel bound to another computer"};
        long now = worldObj.getTotalWorldTime();
        if (now - lastUpdate < 10) return new Object[] {false, "rate limited"};
        String json = PanelModel.normalize(args.checkTable(0));
        if (!payload.equals(json)) { payload = json; revision++; dirty = true; markDirty(); }
        controller = context;
        controllerAddress = address;
        lastUpdate = now;
        if (!online) { online = true; dirty = true; }
        return new Object[] {true};
    }
    @Callback(doc = "getResolution():number,number -- Native pixel resolution.")
    public Object[] getResolution(Context context, Arguments args) { return new Object[] {1280, 720}; }
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
    public void action(EntityPlayerMP player, int action, int request, int value) {
        if (worldObj.isRemote || action < 0 || action > 4 || request<=0 || value<0) return;
        if (!online || controller==null) { reply(player, request, 2, "面板离线，无法提交"); return; }
        if (player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) > 64
                || !controller.canInteract(player.getCommandSenderName())) {
            reply(player, request, 2, "距离过远或没有此 OC 电脑的操作权限"); return;
        }
        long now = worldObj.getTotalWorldTime();
        if (pendingRequest!=0 || now - lastClick < 5) { reply(player, request, 2, "面板正在处理请求，请稍后再试"); return; }
        lastClick = now;
        pendingRequest=request; pendingPlayer=player; pendingSince=now;
        String[] actions = {"refresh", "pause", "resume", "patterns", "cache_target"};
        if (!controller.signal("hd_panel_action", node.address(), actions[action], player.getCommandSenderName(), request, value))
            finish(false, "OC 事件队列未接受请求");
        else reply(player, request, 0, "请求已送达 OC，等待处理回执…");
    }
    private NBTTagCompound snapshot() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("data", payload); nbt.setLong("revision", revision); nbt.setBoolean("online", online);
        return nbt;
    }
    @Override public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, snapshot());
    }
    @Override public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        NBTTagCompound nbt = packet.func_148857_g();
        payload = nbt.getString("data"); revision = nbt.getLong("revision"); online = nbt.getBoolean("online");
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt); payload = nbt.getString("data"); revision = nbt.getLong("revision");
        controllerAddress = nbt.getString("controller"); online = false;
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt); nbt.setString("data", payload); nbt.setLong("revision", revision);
        nbt.setString("controller", controllerAddress);
    }
    @Override public double getMaxRenderDistanceSquared() { return 16 * 16; }
}
