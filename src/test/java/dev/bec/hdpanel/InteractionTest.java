package dev.bec.hdpanel;

import dev.bec.hdpanel.client.PanelInteraction;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractionTest {
    @Test public void rejectsInvalidTargetsAndSupportsEditingSelection() {
        assertNull(PanelInteraction.target("0"));
        assertEquals(Integer.valueOf(1),PanelInteraction.target("1"));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),PanelInteraction.target("2147483647"));
        for(String s:new String[]{"2147483648","-1","1.5","1e6","","500万","999999999999"}) assertNull(PanelInteraction.target(s));
        PanelInteraction ui=new PanelInteraction(); ui.open(4,"144000",true); ui.selected=true;
        ui.replace("5000000"); assertEquals("5000000",ui.input); assertEquals(7,ui.cursor);
        ui.move(2); ui.erase(true); assertEquals("500000",ui.input); assertEquals(1,ui.cursor);
        ui.replace("非法"); assertEquals("500000",ui.input);
        ui.selected=true; ui.erase(false); assertEquals("",ui.input); assertEquals(0,ui.cursor);
    }
    @Test public void onlyMatchingRepliesResolveRequestsAndTimeoutDoesNotClaimSuccess() {
        PanelInteraction ui=new PanelInteraction(); ui.pending(41,1000);
        ui.reply(40,1,"wrong"); assertEquals(41,ui.request);
        ui.reply(41,0,"queued"); assertEquals(41,ui.request);
        ui.reply(41,1,"confirmed"); assertEquals(0,ui.request); assertEquals("confirmed",ui.notice);
        ui.pending(42,1000); ui.tick(13001); assertEquals(0,ui.request); assertTrue(ui.notice.contains("尚未确认"));
        ui.reply(42,1,"late"); assertTrue(ui.notice.contains("尚未确认"));
    }
    @Test public void scrollCanReachLastFluidWithoutPassingBounds() {
        assertEquals(0,PanelInteraction.scroll(0,-10,19));
        assertEquals(14,PanelInteraction.scroll(0,100,19));
        assertEquals(27,PanelInteraction.scroll(20,100,32));
        assertEquals(0,PanelInteraction.scroll(15,0,4));
    }
}
