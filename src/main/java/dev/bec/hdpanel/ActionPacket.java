package dev.bec.hdpanel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class ActionPacket implements IMessage {
    private int x, y, z, action, request, value;
    private String widget="";
    public ActionPacket() { }
    public ActionPacket(PanelTile tile, int action, int request, int value) {
        x = tile.xCoord; y = tile.yCoord; z = tile.zCoord; this.action = action; this.request = request; this.value = value;
    }
    public ActionPacket(PanelTile tile,int request,int value,String widget){this(tile,5,request,value);this.widget=widget;}
    public static ActionPacket bindings(PanelTile tile,int request,String payload) {
        ActionPacket p=new ActionPacket(tile,6,request,0);p.widget=payload;return p;
    }
    @Override public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); action = buf.readUnsignedByte(); request = buf.readInt(); value = buf.readInt();
        widget=ByteBufUtils.readUTF8String(buf);if(widget.length()>(action==6?BindingModel.MAX_PAYLOAD:32))throw new IllegalArgumentException("payload too long");
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeByte(action); buf.writeInt(request); buf.writeInt(value);
        ByteBufUtils.writeUTF8String(buf,widget);
    }
    public static class Handler implements IMessageHandler<ActionPacket, IMessage> {
        @Override public IMessage onMessage(final ActionPacket message, final MessageContext context) {
            CommonProxy.enqueue(new Runnable() {
                @Override public void run() {
                    EntityPlayerMP player = context.getServerHandler().playerEntity;
                    if (player == null || player.playerNetServerHandler == null || !player.playerNetServerHandler.netManager.isChannelOpen()
                        || message.action > 6 || message.request <= 0 || message.value < 0
                        || !player.worldObj.blockExists(message.x, message.y, message.z)) return;
                    TileEntity tile = player.worldObj.getTileEntity(message.x, message.y, message.z);
                    if (tile instanceof PanelTile) ((PanelTile) tile).action(player, message.action, message.request, message.value,message.widget);
                }
            });
            return null;
        }
    }
}
