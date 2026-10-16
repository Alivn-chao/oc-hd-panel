package dev.bec.hdpanel;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;

public class WebMappedFramesTest {
    @Test public void separateMappingsKeepArgbAndIndependentSlots()throws Exception {
        File path=File.createTempFile("ochd-frame-test-",".bin");path.deleteOnExit();
        try(WebMappedFrames producer=new WebMappedFrames(path,true);WebMappedFrames consumer=new WebMappedFrames(path,false)) {
            ByteBuffer pixels=ByteBuffer.allocate(WebMappedFrames.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            pixels.asIntBuffer().put(0,0xFF123456);pixels.asIntBuffer().put(1920*1080-1,0xFFABCDEF);
            producer.write(0,pixels);pixels.asIntBuffer().put(0,0xFF22BDB8);producer.write(1,pixels);
            assertEquals(0xFF123456,consumer.read(0).getRGB(0,0));
            assertEquals(0xFFABCDEF,consumer.read(0).getRGB(1919,1079));
            assertEquals(0xFF22BDB8,consumer.read(1).getRGB(0,0));
        }
    }
    @Test(expected=IOException.class) public void rejectsInvalidSlot()throws Exception {
        File path=File.createTempFile("ochd-frame-test-",".bin");path.deleteOnExit();
        try(WebMappedFrames frames=new WebMappedFrames(path,true)){frames.read(2);}
    }
    @Test(expected=IOException.class) public void rejectsInvalidMappingLength()throws Exception {
        File path=File.createTempFile("ochd-frame-test-",".bin");path.deleteOnExit();
        new WebMappedFrames(path,false);
    }
    @Test(expected=IOException.class) public void rejectsReadsAfterUnmapping()throws Exception {
        File path=File.createTempFile("ochd-frame-test-",".bin");path.deleteOnExit();
        WebMappedFrames frames=new WebMappedFrames(path,true);frames.close();frames.read(0);
    }
}
