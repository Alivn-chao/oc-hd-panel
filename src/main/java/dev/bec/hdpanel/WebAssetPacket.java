package dev.bec.hdpanel;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class WebAssetPacket implements IMessage{
    public String hash="";public int index,total;public byte[] data=new byte[0];
    public WebAssetPacket(){}
    public WebAssetPacket(String h,int i,int n,byte[] bytes){hash=h;index=i;total=n;data=bytes;}
    public void toBytes(ByteBuf b){ByteBufUtils.writeUTF8String(b,hash);b.writeByte(index);b.writeByte(total);b.writeShort(data.length);b.writeBytes(data);}
    public void fromBytes(ByteBuf b){hash=ByteBufUtils.readUTF8String(b);index=b.readUnsignedByte();total=b.readUnsignedByte();int n=b.readUnsignedShort();
        if(!hash.matches("[0-9a-f]{64}")||total<1||total>16||index>=total||n>WebAppModel.CHUNK||n>b.readableBytes())throw new IllegalArgumentException("invalid asset chunk");data=new byte[n];b.readBytes(data);}
    public static class Handler implements IMessageHandler<WebAssetPacket,IMessage>{
        public IMessage onMessage(WebAssetPacket p,MessageContext c){HdPanelMod.proxy.receiveWebAsset(p);return null;}
    }
}
