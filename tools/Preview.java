import dev.bec.hdpanel.client.DashboardPainter;
import dev.bec.hdpanel.client.PanelInteraction;
import dev.bec.hdpanel.client.InteractionPainter;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class Preview {
    public static void main(String[] args) throws Exception {
        String sample = "{\"status\":\"生产中\",\"stage\":\"凝聚物转换 / 连续生产\",\"recipe\":\"原始屏蔽层 · 64 并行\",\"mode\":\"演示数据\",\"parallel\":64,\"maxParallel\":64,\"naniteTier\":4,\"naniteCount\":19612,\"requiredTier\":4,\"cachePercent\":30,\"progress\":440,\"progressMax\":800,\"uptime\":1268,\"controls\":true,\"fluids\":[{\"name\":\"基岩锭\",\"amount\":2210000,\"target\":5000000},{\"name\":\"大弗钙\",\"amount\":0,\"target\":5000000},{\"name\":\"彩色玻璃\",\"amount\":208900,\"target\":5000000},{\"name\":\"宇宙中子素\",\"amount\":4420000,\"target\":5000000},{\"name\":\"维度偏移超流体\",\"amount\":4570000,\"target\":5000000}],\"logs\":[\"演示数据 · 不代表游戏实测\",\"蜂群 T4 可覆盖当前阶段需求\",\"缓存库存已读取，等待下一次状态更新\"]}";
        new File(args[0]).mkdirs();
        sample=sample.substring(0,sample.length()-1)+",\"configTarget\":5000000,\"configEditable\":true}";
        for (int page = 0; page < 4; page++)
            ImageIO.write(DashboardPainter.paint(sample, true, page), "png", new File(args[0], "preview-" + page + ".png"));
        ImageIO.write(DashboardPainter.paint(sample, false, 0), "png", new File(args[0], "preview-offline.png"));
        PanelInteraction ui=new PanelInteraction();ui.open(4,"5000000",true);
        BufferedImage modal=DashboardPainter.paint(sample,true,3);Graphics2D g=modal.createGraphics();
        g.setColor(new Color(15,27,50,128));g.fillRect(0,0,1280,720);
        g.drawImage(InteractionPainter.modal(ui),280,160,null);g.dispose();
        ImageIO.write(modal,"png",new File(args[0],"preview-settings-input.png"));
        ui.open(1,"",true);modal=DashboardPainter.paint(sample,true,0);g=modal.createGraphics();
        g.setColor(new Color(15,27,50,128));g.fillRect(0,0,1280,720);
        g.drawImage(InteractionPainter.modal(ui),280,160,null);g.dispose();
        ImageIO.write(modal,"png",new File(args[0],"preview-confirm.png"));
        long start = System.nanoTime();
        for (int i = 0; i < 30; i++) DashboardPainter.paint(sample, true, 0);
        System.out.println("Rasterization mean ms (30 warm iterations): " + (System.nanoTime()-start)/30.0/1E6);
    }
}
