package dev.bec.hdpanel;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebCommandQueueTest {
    private JsonObject command(String type,String kind,int value){JsonObject c=new JsonObject();c.addProperty("type",type);if(kind!=null)c.addProperty("kind",kind);c.addProperty("value",value);return c;}
    private JsonObject read(WebCommandQueue q){return new JsonParser().parse(q.poll()).getAsJsonObject();}
    @Test public void burstReplacesOldMovesWithoutLosingClickOrder(){
        WebCommandQueue q=new WebCommandQueue(8);
        for(int i=0;i<10000;i++){assertTrue(q.offer(command("input","move",i)));assertTrue(q.offer(command("snapshot",null,i)));}
        assertEquals(2,q.size());assertTrue(q.offer(command("input","down",1)));assertTrue(q.offer(command("input","up",2)));
        assertEquals(9999,read(q).get("value").getAsInt());assertEquals("snapshot",read(q).get("type").getAsString());
        assertEquals("down",read(q).get("kind").getAsString());assertEquals("up",read(q).get("kind").getAsString());assertNull(q.poll());
    }
    @Test public void switchingWorldAndGuiKeepsOnlyTheLatestStreamRate(){
        WebCommandQueue q=new WebCommandQueue(8);
        for(int i=0;i<1000;i++)assertTrue(q.offer(command("stream",null,i%2==0?30:60)));
        assertEquals(1,q.size());assertEquals(60,read(q).get("value").getAsInt());
    }
    @Test public void neverEvictsDiscreteKeysAndKeepsLatestData(){
        WebCommandQueue q=new WebCommandQueue(8);q.offer(command("data",null,1));q.offer(command("input","key",2));q.offer(command("data",null,3));
        assertEquals(2,q.size());assertEquals(2,read(q).get("value").getAsInt());assertEquals(3,read(q).get("value").getAsInt());
        for(int i=0;i<8;i++)assertTrue(q.offer(command("input","key",i)));
        assertFalse(q.offer(command("input","up",9)));assertEquals(8,q.size());
        for(int i=0;i<8;i++)assertEquals(i,read(q).get("value").getAsInt());
    }
    @Test public void hostWaitsForRoomInsteadOfDroppingRelease()throws Exception{
        final WebCommandQueue q=new WebCommandQueue(8);for(int i=0;i<8;i++)q.offer(command("input","key",i));
        Thread producer=new Thread(()->{try{q.put(command("input","up",9));}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        producer.start();assertEquals(0,read(q).get("value").getAsInt());producer.join(2000);assertFalse(producer.isAlive());
        for(int i=1;i<8;i++)read(q);assertEquals("up",read(q).get("kind").getAsString());
    }
}
