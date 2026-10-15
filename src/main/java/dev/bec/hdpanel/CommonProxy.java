package dev.bec.hdpanel;

import java.util.concurrent.ConcurrentLinkedQueue;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class CommonProxy {
    public void receiveReply(ActionReplyPacket reply) { }
    public void receiveWebAsset(WebAssetPacket packet) { }
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    public void init() { FMLCommonHandler.instance().bus().register(this); }
    public void open(PanelTile tile) { }
    public static void enqueue(Runnable task) { if (TASKS.size() < 128) TASKS.add(task); }
    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 64; i++) {
            Runnable task = TASKS.poll();
            if (task == null) break;
            task.run();
        }
    }
}
