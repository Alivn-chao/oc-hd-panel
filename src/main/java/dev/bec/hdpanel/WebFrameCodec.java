package dev.bec.hdpanel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.zip.*;

/** Local IPC frame: fixed 1080p ARGB, lossless zlib at the fastest level. */
public final class WebFrameCodec {
    public static final int WIDTH=1920,HEIGHT=1080;
    private WebFrameCodec(){}
    public static byte[] encode(int[] pixels)throws IOException{
        if(pixels.length!=WIDTH*HEIGHT)throw new IOException("frame dimensions");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(65536);
        DataOutputStream header=new DataOutputStream(bytes);header.writeInt(WIDTH);header.writeInt(HEIGHT);
        Deflater deflater=new Deflater(Deflater.BEST_SPEED);
        try{
            DeflaterOutputStream out=new DeflaterOutputStream(bytes,deflater,32768);byte[] row=new byte[WIDTH*4];
            for(int y=0;y<HEIGHT;y++){for(int x=0;x<WIDTH;x++){int value=pixels[y*WIDTH+x],i=x*4;row[i]=(byte)(value>>>24);row[i+1]=(byte)(value>>>16);row[i+2]=(byte)(value>>>8);row[i+3]=(byte)value;}out.write(row);}
            out.finish();return bytes.toByteArray();
        }finally{deflater.end();}
    }
    public static BufferedImage decode(byte[] bytes)throws IOException{
        DataInputStream header=new DataInputStream(new ByteArrayInputStream(bytes));
        if(header.readInt()!=WIDTH||header.readInt()!=HEIGHT)throw new IOException("frame dimensions");
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        Inflater inflater=new Inflater();
        try{
            DataInputStream in=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes,8,bytes.length-8),inflater,32768));
            byte[] row=new byte[WIDTH*4];for(int y=0;y<HEIGHT;y++){in.readFully(row);for(int x=0;x<WIDTH;x++){int i=x*4;pixels[y*WIDTH+x]=(row[i]&255)<<24|(row[i+1]&255)<<16|(row[i+2]&255)<<8|(row[i+3]&255);}}
            if(in.read()!=-1)throw new IOException("extra frame pixels");return image;
        }finally{inflater.end();}
    }
}
