package dev.bec.hdpanel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;

/** Two local BGRA slots. A producer must wait for an explicit acknowledgement
 * before reusing a slot, so the consumer never reads a partially written frame. */
public final class WebMappedFrames implements Closeable {
    public static final int BYTES=1920*1080*4, SLOTS=2;
    private final RandomAccessFile file;
    private final MappedByteBuffer memory;
    private boolean closed;
    public WebMappedFrames(File path,boolean create)throws IOException {
        file=new RandomAccessFile(path,"rw");
        if(create)file.setLength((long)BYTES*SLOTS);
        if(file.length()!=(long)BYTES*SLOTS){file.close();throw new IOException("Invalid frame mapping size");}
        memory=file.getChannel().map(FileChannel.MapMode.READ_WRITE,0,(long)BYTES*SLOTS);
    }
    private ByteBuffer slot(int index)throws IOException {
        if(closed||index<0||index>=SLOTS)throw new IOException("Closed mapping or invalid frame slot");
        ByteBuffer copy=memory.duplicate();copy.position(index*BYTES);copy.limit((index+1)*BYTES);
        return copy.slice().order(ByteOrder.LITTLE_ENDIAN);
    }
    public synchronized void write(int index,ByteBuffer pixels)throws IOException {
        ByteBuffer source=pixels.duplicate();source.position(0);source.limit(BYTES);
        slot(index).put(source);
    }
    public synchronized BufferedImage read(int index)throws IOException {
        BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
        slot(index).asIntBuffer().get(((DataBufferInt)image.getRaster().getDataBuffer()).getData());
        return image;
    }
    @Override public synchronized void close()throws IOException {
        if(closed)return;closed=true;
        file.close();
        // Release Windows' mapping handle so completed sessions don't retain
        // sixteen MiB temporary files until the whole Minecraft JVM exits.
        try {
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
            unsafe.getMethod("invokeCleaner",ByteBuffer.class).invoke(field.get(null),memory);
        }catch(ReflectiveOperationException ignored){/* Java 8: GC/deleteOnExit fallback. */}
    }
}
