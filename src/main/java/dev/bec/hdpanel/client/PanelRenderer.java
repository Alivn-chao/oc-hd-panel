package dev.bec.hdpanel.client;

import dev.bec.hdpanel.PanelTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class PanelRenderer extends TileEntitySpecialRenderer {
    @Override public void renderTileEntityAt(TileEntity entity, double x, double y, double z, float partial) {
        PanelTile tile = (PanelTile) entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) > 256) return;
        PanelSurfaces.Surface surface = PanelSurfaces.get(tile);
        if (surface == null || surface.location() == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5, y + .5, z + .5);
            GL11.glRotatef(tile.getBlockMetadata() * 90, 0, 1, 0);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1);
            bindTexture(surface.location());
            Tessellator t = Tessellator.instance; t.startDrawingQuads();
            t.setBrightness(0xF000F0);
            t.addVertexWithUV(-.46, -.25875, .501, 0, 1);
            t.addVertexWithUV(.46, -.25875, .501, 1, 1);
            t.addVertexWithUV(.46, .25875, .501, 1, 0);
            t.addVertexWithUV(-.46, .25875, .501, 0, 0);
            t.draw();
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
