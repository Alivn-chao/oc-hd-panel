package dev.bec.hdpanel;

import dev.bec.hdpanel.client.AppPainter;
import dev.bec.hdpanel.client.FramePainter;
import dev.bec.hdpanel.client.InteractionPainter;
import dev.bec.hdpanel.client.PanelInteraction;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class Alpha4PreservationTest {
    @Test public void preserves1080pAndLogicalCoordinateSeparation() {
        assertEquals(1920,PanelResolution.WIDTH);assertEquals(1080,PanelResolution.HEIGHT);
        assertEquals(1280,PanelResolution.UI_WIDTH);assertEquals(720,PanelResolution.UI_HEIGHT);
        BufferedImage app=AppPainter.paint("{}","{}",false,0);
        assertEquals(1920,app.getWidth());assertEquals(1080,app.getHeight());
        PanelInteraction ui=new PanelInteraction();ui.open(4,"144000",true);
        BufferedImage modal=InteractionPainter.modal(ui);assertEquals(1080,modal.getWidth());assertEquals(600,modal.getHeight());
        assertEquals(1992,FramePainter.paint().getWidth());assertEquals(1152,FramePainter.paint().getHeight());
    }
    @Test public void retainsAlpha4FacingAndJoinedEdges() {
        assertEquals(2,PanelFacing.placed(0));assertEquals(1,PanelFacing.placed(90));
        assertEquals(0,PanelFacing.placed(180));assertEquals(3,PanelFacing.placed(270));
        assertEquals(15,PanelFacing.edges(0,0,1,1));assertEquals(0,PanelFacing.edges(1,1,3,3));
        assertEquals(2,PanelFacing.front(2));
    }
}
