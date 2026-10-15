package dev.bec.hdpanel;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Bounded mailbox: replace stale state, retain the order of discrete input. */
public final class WebCommandQueue {
    private static final class Entry {
        final String key, json;
        Entry(String key,String json){this.key=key;this.json=json;}
    }
    private final ArrayDeque<Entry> entries=new ArrayDeque<Entry>();
    private final int capacity;
    public WebCommandQueue(int capacity){if(capacity<8)throw new IllegalArgumentException();this.capacity=capacity;}
    private static String key(JsonObject c){
        String type=c.get("type").getAsString();
        if(type.equals("snapshot")||type.equals("data")||type.equals("zoom"))return type;
        return type.equals("input")&&c.has("kind")&&c.get("kind").getAsString().equals("move")?"move":null;
    }
    public synchronized boolean offer(JsonObject c){
        String key=key(c);
        if(key!=null){Iterator<Entry> it=entries.iterator();while(it.hasNext())if(key.equals(it.next().key))it.remove();}
        if(entries.size()>=capacity){
            Iterator<Entry> it=entries.iterator();boolean removed=false;
            while(it.hasNext())if(it.next().key!=null){it.remove();removed=true;break;}
            if(!removed)return false;
        }
        entries.addLast(new Entry(key,c.toString()));notifyAll();return true;
    }
    /** Host reader may wait; the Minecraft render thread only uses offer. */
    public synchronized void put(JsonObject c)throws InterruptedException{while(!offer(c))wait();}
    public synchronized String poll(){Entry e=entries.pollFirst();notifyAll();return e==null?null:e.json;}
    public synchronized String take()throws InterruptedException{while(entries.isEmpty())wait();return poll();}
    public synchronized String poll(long timeout)throws InterruptedException{if(entries.isEmpty())wait(timeout);return poll();}
    public synchronized int size(){return entries.size();}
    public synchronized void clear(){entries.clear();notifyAll();}
}
