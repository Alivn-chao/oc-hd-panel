package dev.bec.hdpanel;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

/** Display controls are server-owned and remain available while the panel is off. */
public final class PanelSettingsPacket implements IMessage {
    private int x,y,z,brightness;private boolean enabled;
    public PanelSettingsPacket(){}
    public PanelSettingsPacket(PanelTile tile,boolean enabled,int brightness){x=tile.xCoord;y=tile.yCoord;z=tile.zCoord;this.enabled=enabled;this.brightness=brightness;}
    public void fromBytes(ByteBuf b){x=b.readInt();y=b.readInt();z=b.readInt();enabled=b.readBoolean();brightness=b.readUnsignedByte();}
    public void toBytes(ByteBuf b){b.writeInt(x);b.writeInt(y);b.writeInt(z);b.writeBoolean(enabled);b.writeByte(brightness);}
    public static final class Handler implements IMessageHandler<PanelSettingsPacket,IMessage> {
        public IMessage onMessage(final PanelSettingsPacket p,final MessageContext context){
            CommonProxy.enqueue(new Runnable(){public void run(){
                EntityPlayerMP player=context.getServerHandler().playerEntity;
                if(player==null||p.brightness<10||p.brightness>100||!player.worldObj.blockExists(p.x,p.y,p.z))return;
                TileEntity tile=player.worldObj.getTileEntity(p.x,p.y,p.z);
                if(tile instanceof PanelTile)((PanelTile)tile).settings(player,p.enabled,p.brightness);
            }});return null;
        }
    }
}
