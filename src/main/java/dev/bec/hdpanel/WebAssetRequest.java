package dev.bec.hdpanel;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class WebAssetRequest implements IMessage {
    private int x,y,z;private String hash="";
    public WebAssetRequest(){}
    public WebAssetRequest(PanelTile tile,String hash){x=tile.xCoord;y=tile.yCoord;z=tile.zCoord;this.hash=hash;}
    public void toBytes(ByteBuf b){b.writeInt(x);b.writeInt(y);b.writeInt(z);ByteBufUtils.writeUTF8String(b,hash);}
    public void fromBytes(ByteBuf b){x=b.readInt();y=b.readInt();z=b.readInt();hash=ByteBufUtils.readUTF8String(b);if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid hash");}
    public static class Handler implements IMessageHandler<WebAssetRequest,IMessage>{
        public IMessage onMessage(final WebAssetRequest p,final MessageContext c){CommonProxy.enqueue(new Runnable(){public void run(){
            EntityPlayerMP player=c.getServerHandler().playerEntity;if(player==null||!player.worldObj.blockExists(p.x,p.y,p.z))return;
            TileEntity tile=player.worldObj.getTileEntity(p.x,p.y,p.z);if(tile instanceof PanelTile)((PanelTile)tile).sendWebPackage(player,p.hash);
        }});return null;}
    }
}
