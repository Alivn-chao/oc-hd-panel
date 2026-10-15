package dev.bec.hdpanel;
import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.Arrays;
public class WebFrameCodecTest {
    @Test public void keepsExactArgbAnd1080p()throws Exception{int[] pixels=new int[1920*1080];for(int i=0;i<pixels.length;i++)pixels[i]=0xFF000000|((i*731)&0xFFFFFF);pixels[7]=0x6F123456;BufferedImage image=WebFrameCodec.decode(WebFrameCodec.encode(pixels));assertArrayEquals(pixels,((DataBufferInt)image.getRaster().getDataBuffer()).getData());}
    @Test(expected=IOException.class)public void rejectsWrongDimensions()throws Exception{WebFrameCodec.decode(new byte[8]);}
    @Test(expected=IOException.class)public void rejectsTruncatedFrames()throws Exception{byte[] bytes=WebFrameCodec.encode(new int[1920*1080]);WebFrameCodec.decode(Arrays.copyOf(bytes,bytes.length/2));}
}
