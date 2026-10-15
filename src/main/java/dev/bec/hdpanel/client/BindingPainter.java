package dev.bec.hdpanel.client;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Same 1080p white cards, blue navigation and rounded controls as the dashboard. */
public final class BindingPainter {
    public static final int[] SIDES={1,0,3,2,5,4};
    private static final String[] NAMES={"上","下","南","北","东","西"};
    private static final Color INK=new Color(0x203252),MUTED=new Color(0x8290A7),BLUE=new Color(0x6688FF);
    private BindingPainter(){}
    private static void text(Graphics2D g,String s,int x,int y,int size,Color color,int max){
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(color);
        while(g.getFontMetrics().stringWidth(s)>max&&s.length()>1)s=s.substring(0,s.length()-2)+"…";
        g.drawString(s,x,y);
    }
    private static void button(Graphics2D g,int x,int w,String s,boolean primary){
        g.setColor(primary?BLUE:new Color(0xE4EBFF));g.fillRoundRect(x,634,w,40,12,12);
        text(g,s,x+16,660,16,primary?Color.WHITE:BLUE,w-24);
    }
    public static String sideName(String value){for(int i=0;i<SIDES.length;i++)if(value.equals(Integer.toString(SIDES[i])))return NAMES[i];return "请选择方向";}
    public static BufferedImage paint(JsonArray fields,List<String> values,int page,int focus,int cursor,int selection,boolean caret,int dropdown,String notice,boolean pending){
        BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.scale(1.5,1.5);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xEEF1F8));g.fillRect(0,0,1280,720);g.setColor(Color.WHITE);g.fillRect(0,0,1280,72);
        text(g,"BEC",40,45,25,INK,100);text(g,"控制中心",103,44,18,INK,130);
        String[] tabs={"运行总览","凝聚物库存","事件记录","设置"};
        for(int i=0;i<4;i++)text(g,tabs[i],310+i*156,43,16,i==3?BLUE:MUTED,130);
        g.setColor(BLUE);g.fillRoundRect(778,66,98,3,3,3);
        text(g,"设备地址绑定",40,106,20,INK,400);text(g,"纳米蜂群 · ME-IO",880,106,15,BLUE,350);
        g.setColor(new Color(0xDDE2EC));g.fillRoundRect(40,124,1200,464,20,20);g.setColor(Color.WHITE);g.fillRoundRect(40,120,1200,464,20,20);
        for(int i=page*8;i<Math.min(fields.size(),page*8+8);i++){
            int row=i-page*8,y=145+row*48;JsonObject f=fields.get(i).getAsJsonObject();String kind=f.get("kind").getAsString(),value=values.get(i);
            text(g,f.get("label").getAsString(),64,y+22,15,INK,310);
            g.setColor(new Color(0xF5F7FC));g.fillRoundRect(390,y,825,34,8,8);
            g.setColor(row==focus?BLUE:new Color(0xE2E7F0));g.drawRoundRect(390,y,825,34,8,8);
            if("side".equals(kind)&&dropdown==row){
                for(int j=0;j<6;j++){g.setColor(value.equals(Integer.toString(SIDES[j]))?BLUE:new Color(0xE4EBFF));g.fillRoundRect(393+j*137,y+3,130,28,6,6);text(g,NAMES[j],444+j*137,y+23,16,value.equals(Integer.toString(SIDES[j]))?Color.WHITE:INK,60);}
            }else if("side".equals(kind)){text(g,sideName(value),406,y+23,16,INK,760);g.setColor(BLUE);g.fillPolygon(new int[]{1184,1196,1190},new int[]{y+13,y+13,y+20},3);}
            else{
                g.setFont(new Font("Microsoft YaHei",Font.PLAIN,16));FontMetrics fm=g.getFontMetrics();int pos=Math.min(cursor,value.length()),end=Math.min(selection,value.length());
                Shape clip=g.getClip();g.clipRect(400,y+2,804,30);
                if(row==focus&&pos!=end){int a=fm.stringWidth(value.substring(0,Math.min(pos,end))),b=fm.stringWidth(value.substring(0,Math.max(pos,end)));g.setColor(new Color(0xCBD8FF));g.fillRect(406+a,y+5,b-a,25);}
                g.setColor(value.isEmpty()?MUTED:INK);g.drawString(value.isEmpty()?"点击填写，支持 Ctrl+V":value,406,y+23);
                if(row==focus&&caret){int cx=406+fm.stringWidth(value.substring(0,pos));g.setColor(BLUE);g.fillRect(cx,y+6,2,22);}
                g.setClip(clip);
            }
        }
        text(g,"第 "+(page+1)+" / "+Math.max(1,(fields.size()+7)/8)+" 页 · 草稿可以留空，应用时校验完整设备",64,567,14,MUTED,1120);
        text(g,pending?notice:notice,40,613,14,pending?BLUE:MUTED,1190);
        button(g,40,95,"上一页",false);button(g,150,95,"下一页",false);
        button(g,790,130,"暂存草稿",false);button(g,935,130,"应用绑定",true);button(g,1080,150,"返回设置",false);
        text(g,"草稿独立保存，不影响正在使用的配置；返回查地址后可继续填写。",40,703,13,MUTED,1180);
        g.dispose();return image;
    }
}
