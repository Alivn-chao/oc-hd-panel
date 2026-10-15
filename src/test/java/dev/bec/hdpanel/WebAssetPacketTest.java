package dev.bec.hdpanel;
import io.netty.buffer.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebAssetPacketTest{
    @Test public void chunkRoundTripPreservesBinaryAndSequence(){byte[] data=new byte[16384];for(int i=0;i<data.length;i++)data[i]=(byte)i;WebAssetPacket p=new WebAssetPacket(WebAppModel.hash("page"),1,2,data);ByteBuf b=Unpooled.buffer();p.toBytes(b);WebAssetPacket actual=new WebAssetPacket();actual.fromBytes(b);assertEquals(p.hash,actual.hash);assertEquals(1,actual.index);assertEquals(2,actual.total);assertArrayEquals(data,actual.data);}
    @Test(expected=IllegalArgumentException.class) public void impossibleChunkCountRejected(){WebAssetPacket p=new WebAssetPacket(WebAppModel.hash("page"),0,17,new byte[0]);ByteBuf b=Unpooled.buffer();p.toBytes(b);new WebAssetPacket().fromBytes(b);}
}
