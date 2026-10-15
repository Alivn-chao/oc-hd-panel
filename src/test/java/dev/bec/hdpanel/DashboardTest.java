package dev.bec.hdpanel;

import dev.bec.hdpanel.client.DashboardPainter;
import org.junit.Test;
import static org.junit.Assert.*;

public class DashboardTest {
    @Test public void clickAreasMatchRenderedButtons() {
        assertEquals(0, DashboardPainter.actionAt(860, 650));
        assertEquals(3, DashboardPainter.actionAt(970, 650));
        assertEquals(1, DashboardPainter.actionAt(1080, 650));
        assertEquals(2, DashboardPainter.actionAt(1180, 650));
        assertEquals(-1, DashboardPainter.actionAt(860, 610));
        assertEquals(-1, DashboardPainter.actionAt(919, 650));
    }
    @Test public void malformedDataProducesA1080pOfflineFrame() {
        java.awt.image.BufferedImage image = DashboardPainter.paint("invalid-json", false, 0);
        assertEquals(1920, image.getWidth()); assertEquals(1080, image.getHeight());
        assertEquals(0xFFEEF1F8, image.getRGB(10, 710));
    }
}
