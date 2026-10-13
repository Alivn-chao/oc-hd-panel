package dev.bec.hdpanel;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Small response addressed to the requesting player, rather than a shared tile status. */
public class ActionReplyPacket implements IMessage {
    public int x, y, z, request, state;
    public String message;
    public ActionReplyPacket() { }
    public ActionReplyPacket(PanelTile tile, int request, int state, String message) {
        x=tile.xCoord; y=tile.yCoord; z=tile.zCoord; this.request=request; this.state=state;
        this.message=PanelModel.text(message, 128);
    }
    @Override public void fromBytes(ByteBuf b) {
        x=b.readInt(); y=b.readInt(); z=b.readInt(); request=b.readInt(); state=b.readUnsignedByte();
        message=PanelModel.text(ByteBufUtils.readUTF8String(b), 128);
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeInt(x); b.writeInt(y); b.writeInt(z); b.writeInt(request); b.writeByte(state); ByteBufUtils.writeUTF8String(b, message);
    }
    public static class Handler implements IMessageHandler<ActionReplyPacket,IMessage> {
        @Override public IMessage onMessage(ActionReplyPacket message, MessageContext context) {
            HdPanelMod.proxy.receiveReply(message); return null;
        }
    }
}
