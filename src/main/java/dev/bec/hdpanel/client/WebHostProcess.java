package dev.bec.hdpanel.client;

import com.google.gson.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/** Async local process; browser work and stalled JS never run on the Minecraft render thread. */
public final class WebHostProcess {
    private final ArrayBlockingQueue<String> outgoing=new ArrayBlockingQueue<String>(24);
    public final ArrayBlockingQueue<JsonObject> events=new ArrayBlockingQueue<JsonObject>(16);
    public final AtomicReference<BufferedImage> frame=new AtomicReference<BufferedImage>();
    public volatile String error="正在启动网页运行时…";
    public volatile long lastMessage=System.currentTimeMillis();
    private volatile Process process;private volatile boolean closed;
    public WebHostProcess(final File root){
        Thread launcher=new Thread(new Runnable(){public void run(){launch(root);}},"OCHD-Web-launch");launcher.setDaemon(true);launcher.start();
    }
    private void launch(File root){
        try{
            if(!new File(root,"host.jar").isFile())throw new IOException("缺少网页运行时，请解压客户端 runtime 包到游戏目录/ochd-web-runtime");
            boolean windows=System.getProperty("os.name").toLowerCase().contains("win");
            String java=System.getProperty("ochd.web.java",new File(System.getProperty("java.home"),"bin/"+(windows?"javaw.exe":"java")).getPath());
            ProcessBuilder builder=new ProcessBuilder(java,"--module-path",new File(root,"lib").getAbsolutePath(),"--add-modules","javafx.web,javafx.swing","-Dprism.order=sw","-cp",new File(root,"host.jar").getAbsolutePath()+File.pathSeparator+new File(root,"lib/gson.jar").getAbsolutePath(),"dev.bec.hdweb.Host");
            process=builder.start();if(closed){process.destroy();return;}
            Thread writer=new Thread(new Runnable(){public void run(){writeLoop();}},"OCHD-Web-write");writer.setDaemon(true);writer.start();
            Thread logs=new Thread(new Runnable(){public void run(){try{InputStream in=process.getErrorStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){String text=new String(b,0,n,"UTF-8");error="网页运行时："+text.substring(Math.max(0,text.length()-220));}}catch(IOException ignored){}}},"OCHD-Web-errors");logs.setDaemon(true);logs.start();
            DataInputStream input=new DataInputStream(new BufferedInputStream(process.getInputStream()));
            while(!closed){int type=input.readUnsignedByte(),size=input.readInt();if(size<0||size>12*1024*1024)throw new IOException("runtime frame limit");byte[] bytes=new byte[size];input.readFully(bytes);lastMessage=System.currentTimeMillis();
                if(type==1){if(size<24||readInt(bytes,16)!=1920||readInt(bytes,20)!=1080)throw new IOException("runtime image dimensions");BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw new IOException("invalid PNG");frame.set(image);error="";}
                else if(type==2){JsonObject event=new JsonParser().parse(new String(bytes,"UTF-8")).getAsJsonObject();String kind=event.get("type").getAsString();
                    if("error".equals(kind))error=event.get("message").getAsString();else if("event".equals(kind))events.offer(event);}
            }
        }catch(Exception e){if(!closed)error="网页运行时未就绪："+e.getMessage();}finally{if(process!=null)process.destroy();}
    }
    private static int readInt(byte[] b,int i){return (b[i]&255)<<24|(b[i+1]&255)<<16|(b[i+2]&255)<<8|(b[i+3]&255);}
    private void writeLoop(){try{Writer out=new OutputStreamWriter(process.getOutputStream(),"UTF-8");while(!closed){String line=outgoing.poll(1,TimeUnit.SECONDS);if(line!=null){out.write(line);out.write('\n');out.flush();}}}catch(Exception e){if(!closed)error="网页运行时连接中断";}}
    public boolean send(JsonObject command){return !closed&&outgoing.offer(command.toString());}
    public void close(){closed=true;outgoing.clear();frame.set(null);if(process!=null)process.destroy();}
}
