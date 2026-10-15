package dev.bec.hdpanel.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Pure Java2D: executed by a bounded client worker, never on the server or GL thread. */
public final class DashboardPainter {
    public static final int WIDTH = 1920, HEIGHT = 1080;
    private static final Color INK = new Color(0x203252), MUTED = new Color(0x8290A7), BLUE = new Color(0x6688FF);
    private DashboardPainter() { }
    private static String string(JsonObject obj, String key, String fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : fallback;
    }
    private static double number(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsDouble() : 0; } catch (RuntimeException ignored) { return 0; }
    }
    private static String format(double n) {
        if (n >= 1E9) return new DecimalFormat("0.##").format(n / 1E9) + "G";
        if (n >= 1E6) return new DecimalFormat("0.##").format(n / 1E6) + "M";
        if (n >= 1E4) return new DecimalFormat("0.##").format(n / 1E3) + "k";
        return new DecimalFormat("0.##").format(n);
    }
    private static void text(Graphics2D g, String s, int x, int baseline, int size, Color color, int maxWidth) {
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, size)); g.setColor(color);
        while (g.getFontMetrics().stringWidth(s) > maxWidth && s.length() > 1) s = s.substring(0, s.length() - 2) + "…";
        g.drawString(s, x, baseline);
    }
    private static void card(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0xDDE2EC)); g.fillRoundRect(x, y + 4, w, h, 20, 20);
        g.setColor(Color.WHITE); g.fillRoundRect(x, y, w, h, 20, 20);
    }
    private static void metric(Graphics2D g, int x, String title, String value, String detail, int tint) {
        card(g, x, 124, 284, 112);
        g.setColor(new Color(tint)); g.fillRoundRect(x, 124, 6, 112, 6, 6);
        g.setColor(new Color(tint, false)); g.fillRoundRect(x + 20, 145, 42, 42, 12, 12);
        text(g, title.substring(0, 1), x + 30, 174, 22, Color.WHITE, 32);
        text(g, title, x + 78, 155, 16, MUTED, 190);
        text(g, value, x + 78, 189, 27, INK, 190);
        text(g, detail, x + 22, 219, 13, MUTED, 242);
    }
    private static void bar(Graphics2D g, int x, int y, int w, double value, int color) {
        double fraction = Math.max(0, Math.min(1, value));
        g.setColor(new Color(0xEDF0F7)); g.fillRoundRect(x, y, w, 9, 9, 9);
        if (fraction > 0) { g.setColor(new Color(color)); g.fillRoundRect(x, y, Math.max(3, (int)(w * fraction)), 9, 9, 9); }
    }
    public static int actionAt(int x, int y) {
        if (y < 634 || y > 674) return -1;
        if (x >= 820 && x < 915) return 0;
        if (x >= 925 && x < 1020) return 3;
        if (x >= 1030 && x < 1125) return 1;
        if (x >= 1135 && x < 1230) return 2;
        return -1;
    }
    private static void button(Graphics2D g, int x, String label, int color, boolean enabled) {
        g.setColor(new Color(enabled ? color : 0xD8DEE9)); g.fillRoundRect(x, 634, 95, 40, 16, 16);
        text(g, label, x + 16, 660, 16, Color.WHITE, 70);
    }
    public static BufferedImage paint(String payload, boolean online, int page) {
        return paint(payload,online,page,0);
    }
    public static BufferedImage paint(String payload, boolean online, int page, int scroll) {
        JsonObject data;
        try { data = new JsonParser().parse(payload).getAsJsonObject(); } catch (RuntimeException ignored) { data = new JsonObject(); }
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(1.5,1.5);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xEEF1F8)); g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, 72);
        text(g, "BEC", 40, 45, 25, INK, 100);
        text(g, "控制中心", 103, 44, 18, INK, 130);
        String[] tabs = {"运行总览", "凝聚物库存", "事件记录", "设置"};
        int selected = Math.max(0,Math.min(3,page));
        for (int i = 0; i < tabs.length; i++) {
            int x = 310 + i * 156;
            text(g, tabs[i], x, 43, 16, i == selected ? BLUE : MUTED, 130);
            if (i == selected) { g.setColor(BLUE); g.fillRoundRect(x, 66, 98, 3, 3, 3); }
        }
        g.setColor(new Color(online ? 0x23BEA7 : 0xEA9B41)); g.fillOval(1070, 29, 9, 9);
        text(g, online ? "数据已连接" : "等待 OC / 数据已过期", 1090, 42, 14, MUTED, 170);
        text(g, tabs[selected], 40, 105, 20, INK, 600);
        metric(g, 40, "生产并行", format(number(data, "parallel")) + " / " + format(number(data, "maxParallel")),
            "当前配方的并行状态", 0x6688FF);
        metric(g, 345, "缓存达标", format(number(data, "cachePercent")) + "%", "凝聚物补库目标", 0x22BDB8);
        metric(g, 650, "纳米蜂群", "T" + format(number(data, "naniteTier")) + " · " + format(number(data, "naniteCount")),
            "当前需求 T" + format(number(data, "requiredTier")), 0xE7AF3F);
        metric(g, 955, "运行状态", online ? string(data, "status", "等待数据") : "数据未连接",
            string(data, "mode", "未启动"), 0xEA708D);
        JsonArray fluids = data.has("fluids") && data.get("fluids").isJsonArray() ? data.getAsJsonArray("fluids") : new JsonArray();
        JsonArray logs = data.has("logs") && data.get("logs").isJsonArray() ? data.getAsJsonArray("logs") : new JsonArray();
        if (page == 0) {
            card(g, 40, 260, 710, 186);
            text(g, "当前生产任务", 64, 294, 18, BLUE, 640);
            text(g, string(data, "recipe", "等待生产订单"), 64, 334, 25, INK, 640);
            text(g, "阶段  " + string(data, "stage", "尚未读取"), 64, 369, 15, MUTED, 640);
            double max = number(data, "progressMax");
            bar(g, 64, 390, 662, max > 0 ? number(data, "progress") / max : 0, 0x6688FF);
            text(g, max > 0 ? "进度  " + format(number(data, "progress")) + " / " + format(max) : "当前驱动未提供配方进度",
                64, 425, 14, MUTED, 650);
            card(g, 40, 466, 710, 142);
            text(g, "最近事件", 64, 500, 18, BLUE, 640);
            for (int i = Math.max(0, logs.size() - 3), line = 0; i < logs.size(); i++, line++)
                text(g, logs.get(i).getAsString(), 64, 533 + line * 24, 14, MUTED, 640);
            card(g, 774, 260, 466, 348);
            text(g, "凝聚物库存", 798, 294, 18, BLUE, 410);
            for (int i = 0; i < Math.min(5, fluids.size()); i++) fluid(g, fluids.get(i).getAsJsonObject(), 798, 334 + i * 53, 418);
            if (fluids.size() == 0) text(g, "等待约束场库存数据", 798, 341, 16, MUTED, 418);
            text(g, "查看全部库存 →", 798, 585, 14, BLUE, 418);
        } else if (page == 1) {
            card(g, 40, 260, 1200, 348);
            text(g, "凝聚物库存与目标 · mB", 64, 294, 18, BLUE, 1100);
            int offset = PanelInteraction.scroll(scroll,0,fluids.size());
            text(g,"鼠标滚轮 / 拖动滚动条  ·  " + fluids.size() + " 种",870,294,14,MUTED,340);
            for (int i = 0; i < Math.min(5, fluids.size() - offset); i++)
                fluid(g, fluids.get(i + offset).getAsJsonObject(), 64, 334 + i * 53, 1108);
            if(fluids.size()==0) text(g,"尚无库存数据",64,340,16,MUTED,1108);
            g.setColor(new Color(0xEDF0F7)); g.fillRoundRect(1200,315,8,270,8,8);
        } else if (page == 2) {
            card(g, 40, 260, 1200, 348);
            text(g, "事件记录", 64, 294, 18, BLUE, 1100);
            for (int i = 0; i < Math.min(8, logs.size()); i++)
                text(g, logs.get(i).getAsString(), 64, 333 + i * 32, 16, INK, 1135);
        } else {
            card(g,40,260,1200,348);
            text(g,"补库设置",64,298,20,BLUE,1100);
            text(g,"默认目标",64,354,16,MUTED,500);
            text(g,number(data,"configTarget")>0?format(number(data,"configTarget"))+" mB":"等待读取配置",64,399,32,INK,700);
            text(g,"目标应用于未设置单独覆盖值的流体。修改前须暂停服务。",64,445,16,MUTED,1000);
            text(g,"保存到原有配置，新补库计划读取；未完成计划仍按原日志恢复。",64,476,16,MUTED,1000);
            g.setColor(BLUE); g.fillRoundRect(64,514,240,48,14,14);
            text(g,"编辑默认缓存目标",86,545,18,Color.WHITE,208);
            if(data.has("bindings")&&data.getAsJsonArray("bindings").size()>0) {
                g.setColor(BLUE);g.fillRoundRect(330,514,240,48,14,14);
                text(g,"设备地址绑定",352,545,18,Color.WHITE,208);
                text(g,"支持 Ctrl+V 粘贴",600,545,15,MUTED,500);
            } else text(g,online && data.has("configEditable") && data.get("configEditable").getAsBoolean()
                ? "服务已停止，可以保存" : "仅预览 · 当前不可保存",330,545,15,MUTED,800);
        }
        boolean controls = online && data.has("controls") && data.get("controls").getAsBoolean();
        text(g, "运行 " + format(number(data, "uptime")) + " 秒  ·  " + (controls ? "控制接口已启用" : "只读监控"),
            40, 690, 12, MUTED, 760);
        button(g, 820, "刷新", 0x6688FF, online);
        button(g, 925, "处理样板", 0x6688FF, controls);
        button(g, 1030, "暂停", 0xEA708D, controls);
        button(g, 1135, "恢复", 0x22BDB8, controls);
        text(g, "OC HD PANEL  /  1920 × 1080", 40, 704, 11, MUTED, 800);
        g.dispose();
        return image;
    }
    private static void fluid(Graphics2D g, JsonObject row, int x, int baseline, int w) {
        text(g, string(row, "name", "未知流体"), x, baseline, 15, INK, w / 2);
        double amount = number(row, "amount"), target = number(row, "target");
        String count = format(amount) + " / " + (target > 0 ? format(target) : "未设目标");
        text(g, count, x + w / 2, baseline, 14, MUTED, w / 2);
        bar(g, x, baseline + 13, w, target > 0 ? amount / target : 0, 0x9A86EA);
    }
}
