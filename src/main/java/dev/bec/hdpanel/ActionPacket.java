package dev.bec.hdpanel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class ActionPacket implements IMessage {
    private int x, y, z, action, request, value;
    public ActionPacket() { }
    public ActionPacket(PanelTile tile, int action, int request, int value) {
        x = tile.xCoord; y = tile.yCoord; z = tile.zCoord; this.action = action; this.request = request; this.value = value;
    }
    @Override public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); action = buf.readUnsignedByte(); request = buf.readInt(); value = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeByte(action); buf.writeInt(request); buf.writeInt(value);
    }
    public static class Handler implements IMessageHandler<ActionPacket, IMessage> {
        @Override public IMessage onMessage(final ActionPacket message, final MessageContext context) {
            CommonProxy.enqueue(new Runnable() {
                @Override public void run() {
                    EntityPlayerMP player = context.getServerHandler().playerEntity;
                    if (player == null || player.playerNetServerHandler == null || !player.playerNetServerHandler.netManager.isChannelOpen()
                        || message.action > 4 || message.request <= 0 || message.value < 0
                        || !player.worldObj.blockExists(message.x, message.y, message.z)) return;
                    if (player.getDistanceSq(message.x + .5, message.y + .5, message.z + .5) > 64) return;
                    TileEntity tile = player.worldObj.getTileEntity(message.x, message.y, message.z);
                    if (tile instanceof PanelTile) ((PanelTile) tile).action(player, message.action, message.request, message.value);
                }
            });
            return null;
        }
    }
}
