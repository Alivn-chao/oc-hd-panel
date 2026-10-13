package dev.bec.hdpanel.client;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import dev.bec.hdpanel.PanelTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One shared texture per visible screen wall; a single bounded background rasterizer. */
public final class PanelSurfaces {
    private static final Map<PanelTile, Surface> SURFACES = new LinkedHashMap<PanelTile, Surface>(16, .75F, true);
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(8), new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread t = new Thread(task, "OC-HD-Painter"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
            }
        });
    private PanelSurfaces() { }
    public static Surface get(PanelTile tile) { return get(tile, false); }
    public static Surface get(PanelTile tile, boolean gui) {
        tile=tile.master();
        Surface s = SURFACES.get(tile);
        if (s == null) {
            if (SURFACES.size() >= 4) {
                Iterator<Surface> it = SURFACES.values().iterator(); Surface old = it.next();
                // Avoid texture allocation/eviction every frame when many panels share a view.
                if (!gui && System.currentTimeMillis() - old.lastUsed < 5000) return null;
                old.dispose(); it.remove();
            }
            s = new Surface(); SURFACES.put(tile, s);
        }
        s.refresh(tile,gui);
        return s;
    }
    public static void prune() {
        Minecraft mc = Minecraft.getMinecraft();
        Iterator<Map.Entry<PanelTile, Surface>> it = SURFACES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PanelTile, Surface> e = it.next(); PanelTile tile = e.getKey();
            if (mc.theWorld == null || tile.getWorldObj() != mc.theWorld || tile.isInvalid()
                || mc.thePlayer == null || tile.master()!=tile || tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ) > 36 * 36) {
                e.getValue().dispose(); it.remove();
            }
        }
    }
    public static final class Surface {
        private DynamicTexture texture;
        private ResourceLocation location;
        private Future<BufferedImage> pending;
        private String scheduled = "";
        private long submitted;
        private long lastUsed;
        public int page;
        public int scroll;
        private int lastPage=-1, lastScroll=-1;
        private String lastApp="",lastLayout="";
        public String error = "";
        public ResourceLocation location() { return location; }
        private void refresh(final PanelTile tile, boolean gui) {
            lastUsed = System.currentTimeMillis();
            if(!lastApp.equals(tile.app)||!lastLayout.equals(tile.layout)) {page=0;scroll=0;lastApp=tile.app;lastLayout=tile.layout;}
            if (pending != null && pending.isDone()) {
                try {
                    BufferedImage image = pending.get();
                    if (texture == null) {
                        texture = new DynamicTexture(DashboardPainter.WIDTH, DashboardPainter.HEIGHT);
                        location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("ochdpanel", texture);
                    }
                    image.getRGB(0, 0, image.getWidth(), image.getHeight(), texture.getTextureData(), 0, image.getWidth());
                    texture.updateDynamicTexture();
                    Minecraft.getMinecraft().getTextureManager().bindTexture(location);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                    error = "";
                } catch (Exception ex) { error = ex.getClass().getSimpleName(); scheduled = ""; }
                pending = null;
            }
            final String key = tile.revision + ":" + tile.online + ":" + page + ":" + scroll;
            long now = System.currentTimeMillis();
            boolean interaction=gui && (page!=lastPage || scroll!=lastScroll);
            if (pending != null || key.equals(scheduled) || now - submitted < (interaction?33:500)) return;
            final String payload=tile.payload,app=tile.app,layout=tile.layout,values=tile.values;final boolean online=tile.online;final int view=page,offset=scroll;
            try {
                pending = WORKER.submit(new java.util.concurrent.Callable<BufferedImage>() {
                    @Override public BufferedImage call() { return "bec_dashboard".equals(app)?DashboardPainter.paint(payload,online,view,offset):AppPainter.paint(layout,values,online,view); }
                });
                scheduled = key; submitted = now; lastPage=page; lastScroll=scroll;
            } catch (RejectedExecutionException ignored) { }
        }
        private void dispose() {
            if (pending != null) pending.cancel(false);
            if (location != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        }
    }
}
