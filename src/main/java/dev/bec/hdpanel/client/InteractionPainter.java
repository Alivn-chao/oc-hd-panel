package dev.bec.hdpanel.client;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Small cached modal/status textures; the 720p dashboard stays untouched by keyboard input. */
public final class InteractionPainter {
    private static final Color BLUE=new Color(0x6688FF), INK=new Color(0x203252), MUTED=new Color(0x8290A7);
    private InteractionPainter() { }
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g=image.createGraphics();
        g.scale(1.5,1.5);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON); return g;
    }
    private static void text(Graphics2D g,String s,int x,int y,int size,Color color,int width) {
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size)); g.setColor(color);
        while(g.getFontMetrics().stringWidth(s)>width && s.length()>1) s=s.substring(0,s.length()-2)+"…";
        g.drawString(s,x,y);
    }
    private static void button(Graphics2D g,int x,int y,int width,String label,boolean enabled) {
        g.setColor(enabled?BLUE:new Color(0xCBD3E4)); g.fillRoundRect(x,y,width,42,12,12);
        text(g,label,x+18,y+28,16,Color.WHITE,width-24);
    }
    public static BufferedImage modal(PanelInteraction state) {
        BufferedImage image=new BufferedImage(1080,600,BufferedImage.TYPE_INT_ARGB); Graphics2D g=graphics(image);
        g.setColor(Color.WHITE); g.fillRoundRect(0,0,720,400,24,24);
        String[] names={"刷新数据","暂停服务","恢复 / 重试","处理样板","设置缓存目标","确认操作"};
        text(g,state.modal==5?state.customLabel:names[state.modal],28,48,24,INK,664);
        if(state.modal==4) {
            text(g,"默认补库目标 · mB  /  各流体的单独覆盖值保持不变",28,86,16,MUTED,664);
            button(g,28,116,200,"144,000 mB",true); button(g,244,116,200,"1,000,000 mB",true);
            button(g,460,116,200,"5,000,000 mB",true);
            text(g,"自定义整数目标",28,183,14,MUTED,664);
            g.setColor(new Color(0xF3F5FA)); g.fillRoundRect(28,194,664,56,10,10);
            g.setColor(state.focused?BLUE:new Color(0xDDE3EF)); g.drawRoundRect(28,194,663,55,10,10);
            if(state.selected) { g.setColor(new Color(0xDDE6FF)); g.fillRect(46,205,Math.max(12,advance(state.input)),34); }
            text(g,state.input,46,232,24,INK,580); text(g,"mB",642,232,18,MUTED,46);
            text(g,state.editable?"默认目标须为正整数：1–2147483647 mB，单项覆盖值保持不变。":"当前只读或服务运行中：可预览输入，暂停后才能保存。",28,282,14,MUTED,664);
        } else {
            String[] detail={"请求下一份 OC 状态快照。","通过原控制器的 Q 入口暂停服务。","通过原控制器的恢复 / T 重试入口执行。","通过原控制器的 S 入口处理样板。"};
            text(g,state.modal==5?state.customConfirm:detail[state.modal],28,103,19,INK,664);
            text(g,"提交后显示 OC 回执。机器执行结果请以运行状态与日志为准。",28,155,16,MUTED,664);
            text(g,state.modal==5?"操作事件会传给绑定此屏幕的自动化程序。":"此操作由现有 BEC 控制器处理。",28,193,16,MUTED,664);
        }
        text(g,state.notice,28,313,14,new Color(0xD47D38),664);
        button(g,420,338,124,"取消",true);
        button(g,560,338,132,state.modal==4?"保存设置":"确认操作",state.modal!=4 || state.editable);
        g.dispose(); return image;
    }
    public static int advance(String input) {
        BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB); Graphics2D g=graphics(image);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,24)); int w=g.getFontMetrics().stringWidth(input); g.dispose(); return w;
    }
    public static BufferedImage status(String message, boolean pending) {
        BufferedImage image=new BufferedImage(1140,57,BufferedImage.TYPE_INT_ARGB); Graphics2D g=graphics(image);
        g.setColor(new Color(pending?0xE4EBFF:0xE4F3F0)); g.fillRoundRect(0,0,760,38,10,10);
        text(g,message,14,25,15,INK,730); g.dispose(); return image;
    }
}
