package dev.bec.hdpanel.client;

import com.google.gson.JsonObject;
import dev.bec.hdpanel.PanelTile;
import dev.bec.hdpanel.WebViewport;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Interactive HTML view; the same browser texture is used on the physical screen wall. */
public final class WebPanelGui extends GuiScreen {
    public final PanelTile tile;private int left,top,w,h;private static boolean expanded=true;private static int zoom=125;private boolean pressed;private WebViewport viewport;
    private DynamicTexture frameTexture;private ResourceLocation frameLocation;private long lastMove;private int lastX=-1,lastY=-1;
    public WebPanelGui(PanelTile tile){this.tile=tile.master();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui(){Keyboard.enableRepeatEvents(true);applyZoom();}
    @Override public void onGuiClosed(){if(pressed)mouse("up",Math.max(left,Math.min(left+w-1,lastX)),Math.max(top,Math.min(top+h-1,lastY)));Keyboard.enableRepeatEvents(false);if(frameLocation!=null)mc.getTextureManager().deleteTexture(frameLocation);frameLocation=null;frameTexture=null;}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        if(!tile.displayActive()){mc.displayGuiScreen(new ScreenSettingsGui(tile));return;}
        if(tile.isInvalid()||tile.getWorldObj()!=mc.theWorld||tile.master()!=tile||tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>64||!"web".equals(tile.app)){mc.displayGuiScreen(null);return;}
        drawDefaultBackground();viewport=new WebViewport(width,height,expanded);double scale=viewport.scale;w=viewport.width;h=viewport.height;left=viewport.left;top=viewport.top;
        if(frameLocation==null){BufferedImage image=FramePainter.paint();frameTexture=new DynamicTexture(image.getWidth(),image.getHeight());frameLocation=mc.getTextureManager().getDynamicTextureLocation("ochd-web-frame",frameTexture);image.getRGB(0,0,image.getWidth(),image.getHeight(),frameTexture.getTextureData(),0,image.getWidth());frameTexture.updateDynamicTexture();}
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);try{GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glColor4f(1,1,1,1);mc.getTextureManager().bindTexture(frameLocation);double border=24*scale;Tessellator f=Tessellator.instance;f.startDrawingQuads();f.setColorOpaque_F(1,1,1);f.addVertexWithUV(left-border,top+h+border,0,0,1);f.addVertexWithUV(left+w+border,top+h+border,0,1,1);f.addVertexWithUV(left+w+border,top-border,0,1,0);f.addVertexWithUV(left-border,top-border,0,0,0);f.draw();}finally{GL11.glPopAttrib();}
        WebScreens.View view=WebScreens.get(tile,true);if(view!=null)view.zoom(zoom/100.0);
        if(view!=null&&view.location()!=null){GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);try{mc.getTextureManager().bindTexture(view.location());GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glColor4f(1,1,1,1);Tessellator t=Tessellator.instance;t.startDrawingQuads();float bright=tile.displayBrightness();t.setColorOpaque_F(bright,bright,bright);t.addVertexWithUV(left,top+h,0,0,1);t.addVertexWithUV(left+w,top+h,0,1,1);t.addVertexWithUV(left+w,top,0,1,0);t.addVertexWithUV(left,top,0,0,0);t.draw();}finally{GL11.glPopAttrib();}}
        else drawCenteredString(fontRendererObj,view==null?"等待网页显示资源":view.error,width/2,height/2,0xFFFFFF);
        drawCenteredString(fontRendererObj,!tile.online?"OC离线 · 当前数据可能已过期":view!=null&&!view.error.isEmpty()?view.error:"Ctrl+滚轮缩放 · F6全屏 · Ctrl+V粘贴 · Esc返回",width/2,height-12,0xD8E1F4);
        drawTabs(mouseX,mouseY);
    }
    private void drawTabs(int mouseX,int mouseY){
        int hover=viewport.tabAt(mouseX,mouseY);
        String[] labels={"","−",zoom+"%","+","1:1",""};
        String[] tips={expanded?"还原窗口 · F6":"全屏显示 · F6","缩小页面", "当前缩放；点击恢复100%", "放大页面", "恢复100%", "返回游戏 · Esc"};
        for(int i=0;i<6;i++){
            int x=viewport.tabLeft(i),y=viewport.tabTop-(i==hover?2:0),width=WebViewport.TAB_WIDTHS[i],bottom=viewport.frameTop+1;
            drawRect(x+2,y,x+width-2,bottom,0xFF26334A);drawRect(x,y+3,x+width,bottom,0xFF26334A);
            drawRect(x+2,y+2,x+width-2,bottom-2,0xFF56647D);drawRect(x+3,y+3,x+width-3,bottom-2,i==hover?0xFF3B4D69:0xFF272F40);
            drawRect(x+4,y+3,x+width-4,y+5,0xFF77869E);drawRect(x+width-4,y+5,x+width-2,bottom-2,0xFF111722);
            if(i==hover)drawRect(x+5,y+6,x+width-5,y+8,0xFF22BDB8);
            int cy=y+13,color=0xFFB7D6ED;
            if(i==0){int cx=x+width/2;for(int dx:new int[]{-7,3})for(int dy:new int[]{-6,3}){drawRect(cx+dx,cy+dy,cx+dx+5,cy+dy+2,color);drawRect(cx+(dx<0?-7:6),cy+dy,cx+(dx<0?-5:8),cy+dy+5,color);}}
            else if(i==5){int cx=x+width/2;drawRect(cx-6,cy,cx+7,cy+2,color);for(int k=0;k<5;k++)drawRect(cx-7+k,cy-k,cx-5+k,cy+3+k,color);}
            else fontRendererObj.drawString(labels[i],x+(width-fontRendererObj.getStringWidth(labels[i]))/2,cy-3,0xB7D6ED);
        }
        if(hover>=0){String text=tips[hover];int x=Math.min(width-fontRendererObj.getStringWidth(text)-14,Math.max(4,mouseX+8)),y=viewport.frameTop+4;drawRect(x,y,x+fontRendererObj.getStringWidth(text)+10,y+17,0xF5272F40);fontRendererObj.drawString(text,x+5,y+5,0xB7D6ED);}
    }

    private void applyZoom(){WebScreens.View v=WebScreens.get(tile,true);if(v!=null)v.zoom(zoom/100.0);}
    private void changeZoom(int value){zoom=Math.max(50,Math.min(250,value));applyZoom();}
    private boolean inside(int x,int y){return x>=left&&x<left+w&&y>=top&&y<top+h;}
    private int x(int raw){return w==0?0:(raw-left)*1280/w;}private int y(int raw){return h==0?0:(raw-top)*720/h;}
    private JsonObject input(String kind){JsonObject c=new JsonObject();c.addProperty("kind",kind);c.addProperty("shift",isShiftKeyDown());c.addProperty("ctrl",isCtrlKeyDown());c.addProperty("alt",Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU));return c;}
    private void send(JsonObject c){WebScreens.View view=WebScreens.get(tile,true);if(view!=null)view.input(c);}
    private void mouse(String kind,int rawX,int rawY){if(kind.equals("move")){long now=System.currentTimeMillis();if((rawX==lastX&&rawY==lastY)||now-lastMove<16)return;lastMove=now;}lastX=rawX;lastY=rawY;int xx=x(rawX),yy=y(rawY);if(xx<0||xx>=1280||yy<0||yy>=720)return;JsonObject c=input(kind);c.addProperty("x",xx);c.addProperty("y",yy);send(c);}
    @Override protected void mouseClicked(int x,int y,int button){if(button!=0)return;int i=viewport==null?-1:viewport.tabAt(x,y);if(i>=0){if(i==0)expanded=!expanded;else if(i==1)changeZoom(zoom-25);else if(i==3)changeZoom(zoom+25);else if(i==2||i==4)changeZoom(100);else if(i==5)mc.displayGuiScreen(null);return;}pressed=inside(x,y);if(pressed)mouse("down",x,y);}
    @Override protected void mouseMovedOrUp(int x,int y,int button){if(button==0&&pressed){mouse("up",Math.max(left,Math.min(left+w-1,x)),Math.max(top,Math.min(top+h-1,y)));pressed=false;}}
    @Override protected void mouseClickMove(int x,int y,int button,long time){mouse("move",x,y);}
    @Override public void handleMouseInput(){super.handleMouseInput();if(Mouse.getEventDX()!=0||Mouse.getEventDY()!=0)mouse("move",Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1);int wheel=Mouse.getEventDWheel();if(wheel!=0&&isCtrlKeyDown()){changeZoom(zoom+(wheel>0?25:-25));return;}if(wheel!=0&&inside(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1)){JsonObject c=input("wheel");c.addProperty("x",x(Mouse.getEventX()*width/mc.displayWidth));c.addProperty("y",y(height-Mouse.getEventY()*height/mc.displayHeight-1));c.addProperty("delta",wheel>0?80:-80);send(c);}}
    @Override protected void keyTyped(char ch,int key){
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(null);return;}if(key==Keyboard.KEY_F6){expanded=!expanded;return;}
        if(isCtrlKeyDown()&&(key==Keyboard.KEY_EQUALS||key==Keyboard.KEY_ADD)){changeZoom(zoom+25);return;}if(isCtrlKeyDown()&&(key==Keyboard.KEY_MINUS||key==Keyboard.KEY_SUBTRACT)){changeZoom(zoom-25);return;}if(isCtrlKeyDown()&&key==Keyboard.KEY_0){changeZoom(100);return;}
        if(isCtrlKeyDown()&&key==Keyboard.KEY_V){JsonObject c=input("paste");String text=getClipboardString();c.addProperty("text",text.length()>4096?text.substring(0,4096):text);send(c);return;}
        String code=key==Keyboard.KEY_BACK?"BACK_SPACE":key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER?"ENTER":key==Keyboard.KEY_LCONTROL||key==Keyboard.KEY_RCONTROL?"CONTROL":key==Keyboard.KEY_LSHIFT||key==Keyboard.KEY_RSHIFT?"SHIFT":key==Keyboard.KEY_LMENU||key==Keyboard.KEY_RMENU?"ALT":key==Keyboard.KEY_PRIOR?"PAGE_UP":key==Keyboard.KEY_NEXT?"PAGE_DOWN":key==Keyboard.KEY_SPACE?"SPACE":Keyboard.getKeyName(key);
        if(code!=null&&code.matches("[0-9]"))code="DIGIT"+code;
        JsonObject c=input("key");c.addProperty("key",code==null?"UNDEFINED":code);c.addProperty("character",ch>=32?String.valueOf(ch):"");send(c);
    }
}
