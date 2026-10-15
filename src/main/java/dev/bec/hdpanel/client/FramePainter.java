/*
 * Decompiled with CFR 0.152.
 */
package dev.bec.hdpanel.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class FramePainter {
    public static final int BORDER = 24;

    private FramePainter() {
    }

    public static BufferedImage paint() {
        BufferedImage image = new BufferedImage(1992, 1152, 2);
        Graphics2D g = image.createGraphics();
        g.scale(1.5, 1.5);
        int w = 1328;
        int h = 768;
        g.setColor(new Color(0x111722));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(5661821));
        g.fillRect(3, 3, w - 6, h - 6);
        g.setColor(new Color(7833246));
        g.fillRect(5, 5, w - 10, 3);
        g.fillRect(5, 5, 3, h - 10);
        g.setColor(new Color(2107704));
        g.fillRect(5, h - 8, w - 10, 3);
        g.fillRect(w - 8, 5, 3, h - 10);
        g.setColor(new Color(2568000));
        g.fillRect(9, 9, w - 18, h - 18);
        g.setColor(new Color(725274));
        g.fillRect(19, 19, w - 38, h - 38);
        g.setColor(new Color(2276792));
        g.fillRect(w - 94, h - 16, 54, 4);
        g.setColor(new Color(8556711));
        for (int x : new int[]{13, w - 16}) {
            for (int y : new int[]{13, h - 16}) {
                g.fillRect(x, y, 3, 3);
            }
        }
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(24, 24, 1280, 720);
        g.dispose();
        return image;
    }
}

