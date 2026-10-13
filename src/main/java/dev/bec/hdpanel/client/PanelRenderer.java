package dev.bec.hdpanel.client;

import dev.bec.hdpanel.PanelTile;
import dev.bec.hdpanel.WallGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class PanelRenderer extends TileEntitySpecialRenderer {
    @Override public void renderTileEntityAt(TileEntity entity, double x, double y, double z, float partial) {
        PanelTile tile = (PanelTile) entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || tile.master().distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ) > 1024) return;
        PanelSurfaces.Surface surface = PanelSurfaces.get(tile);
        if (surface == null || surface.location() == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5, y + .5, z + .5);
            GL11.glRotatef(tile.getBlockMetadata() * 90, 0, 1, 0);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1);
            double left=tile.column==0?-.42:-.5,right=tile.column==tile.wallWidth-1?.42:.5;
            double bottom=tile.row==0?-.42:-.5,top=tile.row==tile.wallHeight-1?.42:.5;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Tessellator t = Tessellator.instance; t.startDrawingQuads();
            t.setBrightness(0xF000F0);
            t.setColorOpaque_F(.04F,.05F,.07F);
            t.addVertex(left,bottom,.501);t.addVertex(right,bottom,.501);t.addVertex(right,top,.501);t.addVertex(left,top,.501);t.draw();
            double[] s=new WallGeometry(tile.wallWidth,tile.wallHeight).slice(tile.column,tile.row);if(s==null)return;
            GL11.glEnable(GL11.GL_TEXTURE_2D);bindTexture(surface.location());t.startDrawingQuads();t.setColorOpaque_F(1,1,1);t.setBrightness(0xF000F0);
            t.addVertexWithUV(s[0],s[1],.502,s[4],s[7]);t.addVertexWithUV(s[2],s[1],.502,s[6],s[7]);
            t.addVertexWithUV(s[2],s[3],.502,s[6],s[5]);t.addVertexWithUV(s[0],s[3],.502,s[4],s[5]);
            t.draw();
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
