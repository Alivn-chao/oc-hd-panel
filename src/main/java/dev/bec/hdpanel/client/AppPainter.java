package dev.bec.hdpanel.client;

import dev.bec.hdpanel.AppModel;
import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class AppPainter {
    private AppPainter() { }
    private static void text(Graphics2D g,String s,int x,int y,int size,int color,int width) {
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(new Color(color));
        while(g.getFontMetrics().stringWidth(s)>width&&s.length()>1)s=s.substring(0,s.length()-2)+"…";
        g.drawString(s,x,y);
    }
    private static String str(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    private static double n(JsonObject o,String key){return o.has(key)?o.get(key).getAsDouble():0;}
    public static int tabAt(String layout,int x,int y) {
        JsonObject root=AppModel.parse(layout);int count=root.has("pages")?root.getAsJsonArray("pages").size():0;
        int w=count==0?156:Math.min(156,700/count);return count>0&&y>=15&&y<65&&x>=300&&x<300+w*count?(x-300)/w:-1;
    }
    public static BufferedImage paint(String layout,String values,boolean online,int page) {
        JsonObject root=AppModel.parse(layout);JsonArray pages=root.has("pages")?root.getAsJsonArray("pages"):new JsonArray();
        BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xEEF1F8));g.fillRect(0,0,1280,720);g.setColor(Color.WHITE);g.fillRect(0,0,1280,72);
        text(g,str(root,"title","OC 高清屏幕"),40,46,24,0x203252,240);
        int tabWidth=pages.size()==0?156:Math.min(156,700/pages.size());
        for(int i=0;i<pages.size();i++) {
            text(g,str(pages.get(i).getAsJsonObject(),"label","页面"),310+i*tabWidth,44,16,i==page?0x6688FF:0x8290A7,tabWidth-20);
            if(i==page){g.setColor(new Color(0x6688FF));g.fillRoundRect(310+i*tabWidth,66,tabWidth-30,3,3,3);}
        }
        text(g,online?"应用已连接":"未连接 / 数据已过期",1070,44,14,0x8290A7,185);
        if(pages.size()==0) {
            g.setColor(Color.WHITE);g.fillRoundRect(160,185,960,350,24,24);
            text(g,"OC 高清屏幕",200,258,32,0x203252,880);
            text(g,"等待自动化程序",200,314,22,0x6688FF,880);
            text(g,"接入 OC 线缆，由 Lua 定义你自己的页面、数据和按钮。",200,367,18,0x8290A7,880);
            text(g,"同朝向的小屏组成矩形后会自动合并为一面大屏。",200,412,18,0x8290A7,880);
            text(g,"通用接口：setUI / updateUI / hd_panel_event",200,481,16,0x8290A7,880);
        } else {
            for(JsonElement element:AppModel.widgets(layout,page)) {
                JsonObject w=element.getAsJsonObject(),v=AppModel.value(values,w.get("id").getAsString());
                int x=w.get("x").getAsInt(),y=w.get("y").getAsInt(),width=w.get("w").getAsInt(),height=w.get("h").getAsInt();
                String type=w.get("type").getAsString(),label=w.get("label").getAsString();
                boolean control=type.equals("button")||type.equals("toggle"),enabled=online&&v.has("enabled")&&v.get("enabled").getAsBoolean();
                boolean active=v.has("active")&&v.get("active").getAsBoolean();
                g.setColor(new Color(control?(enabled?(active?0x22BDB8:0x6688FF):0xCBD3E4):0xFFFFFF));g.fillRoundRect(x,y,width,height,18,18);
                if(control) {
                    text(g,label+(type.equals("toggle")?(active?" · 开":" · 关"):""),x+20,y+height/2+7,18,0xFFFFFF,width-40);
                } else {
                    if(type.equals("text")&&height<85){text(g,str(v,"text",label),x+20,y+height/2+7,18,0x203252,width-40);continue;}
                    text(g,label,x+20,y+30,16,0x8290A7,width-40);
                    text(g,str(v,"text","")+str(w,"unit",""),x+20,y+Math.min(75,height-14),type.equals("text")?18:28,0x203252,width-40);
                    if(type.equals("progress")&&height>=100) {
                        double max=n(v,"max"),fraction=max>0?Math.max(0,Math.min(1,n(v,"value")/max)):0;
                        g.setColor(new Color(0xEDF0F7));g.fillRoundRect(x+20,y+height-28,width-40,9,9,9);
                        g.setColor(new Color(0x22BDB8));g.fillRoundRect(x+20,y+height-28,(int)((width-40)*fraction),9,9,9);
                    }
                }
            }
        }
        text(g,str(root,"subtitle",""),40,694,14,0x8290A7,1150);text(g,"OC HD PANEL  /  1280 × 720",40,713,10,0x8290A7,800);
        g.dispose();return image;
    }
}
