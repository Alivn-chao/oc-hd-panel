package dev.bec.hdpanel.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.bec.hdpanel.CommonProxy;
import dev.bec.hdpanel.PanelTile;
import dev.bec.hdpanel.ActionReplyPacket;
import java.util.concurrent.ArrayBlockingQueue;
import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {
    private int ticks;
    private final ArrayBlockingQueue<ActionReplyPacket> replies = new ArrayBlockingQueue<ActionReplyPacket>(32);
    @Override public void receiveWebAsset(dev.bec.hdpanel.WebAssetPacket packet){WebScreens.receive(packet);}
    @Override public void receiveReply(ActionReplyPacket reply) { replies.offer(reply); }
    @Override public void init() {
        super.init();
        ClientRegistry.bindTileEntitySpecialRenderer(PanelTile.class, new PanelRenderer());
    }
    @Override public void open(PanelTile tile) { Minecraft.getMinecraft().displayGuiScreen("web".equals(tile.master().app)?new WebPanelGui(tile):new PanelGui(tile)); }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ActionReplyPacket reply;
        while ((reply=replies.poll())!=null) {
            WebScreens.reply(reply);
            if (Minecraft.getMinecraft().currentScreen instanceof PanelGui)
                ((PanelGui) Minecraft.getMinecraft().currentScreen).receive(reply);
            else if(Minecraft.getMinecraft().currentScreen instanceof BindingGui)
                ((BindingGui)Minecraft.getMinecraft().currentScreen).receive(reply);
        }
        WebScreens.tick();
        if (++ticks % 40 == 0) PanelSurfaces.prune();
    }
}
