package dev.bec.hdpanel.client;

import com.google.gson.JsonObject;
import dev.bec.hdpanel.PanelTile;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Interactive HTML view; the same browser texture is used on the physical screen wall. */
public final class WebPanelGui extends GuiScreen {
    public final PanelTile tile;private int left,top,w,h;private static boolean expanded=true;private static int zoom=125;private boolean pressed;private final int[] buttons={6,70,98,144,172,218};private final int[] sizes={60,24,42,24,42,48};
    public WebPanelGui(PanelTile tile){this.tile=tile.master();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui(){Keyboard.enableRepeatEvents(true);applyZoom();}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        if(tile.isInvalid()||tile.getWorldObj()!=mc.theWorld||tile.master()!=tile||tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>64||!"web".equals(tile.app)){mc.displayGuiScreen(null);return;}
        drawDefaultBackground();double scale=Math.max(.01,Math.min((width-(expanded?8:48))/1280.0,(height-(expanded?48:88))/720.0));w=(int)(1280*scale);h=(int)(720*scale);left=(width-w)/2;top=30+Math.max(0,(height-48-h)/2);
        WebScreens.View view=WebScreens.get(tile,true);if(view!=null)view.zoom(zoom/100.0);
        if(view!=null&&view.location()!=null){GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);try{mc.getTextureManager().bindTexture(view.location());GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glColor4f(1,1,1,1);Tessellator t=Tessellator.instance;t.startDrawingQuads();t.setColorOpaque_F(1,1,1);t.addVertexWithUV(left,top+h,0,0,1);t.addVertexWithUV(left+w,top+h,0,1,1);t.addVertexWithUV(left+w,top,0,1,0);t.addVertexWithUV(left,top,0,0,0);t.draw();}finally{GL11.glPopAttrib();}}
        else drawCenteredString(fontRendererObj,view==null?"等待网页显示资源":view.error,width/2,height/2,0xFFFFFF);
        drawCenteredString(fontRendererObj,!tile.online?"OC离线 · 当前数据可能已过期":view!=null&&!view.error.isEmpty()?view.error:"Ctrl+滚轮缩放 · F6全屏 · Ctrl+V粘贴 · Esc返回",width/2,height-12,0xD8E1F4);
        drawRect(0,0,width,26,0xFFF3F6FC);drawRect(0,25,width,26,0xFFCCD8EC);
        String[] labels={expanded?"还原窗口":"全屏显示","−",zoom+"%","+","重置","返回"};
        for(int i=0;i<buttons.length;i++){int bx=buttons[i];boolean hover=mouseX>=bx&&mouseX<bx+sizes[i]&&mouseY>=4&&mouseY<22;drawRect(bx,4,bx+sizes[i],22,hover?0xFFD6E6FF:0xFFE4EDFA);fontRendererObj.drawString(labels[i],bx+(sizes[i]-fontRendererObj.getStringWidth(labels[i]))/2,9,0x245696);}
    }
    private void applyZoom(){WebScreens.View v=WebScreens.get(tile,true);if(v!=null)v.zoom(zoom/100.0);}
    private void changeZoom(int value){zoom=Math.max(50,Math.min(250,value));applyZoom();}
    private boolean inside(int x,int y){return x>=left&&x<left+w&&y>=top&&y<top+h;}
    private int x(int raw){return w==0?0:(raw-left)*1280/w;}private int y(int raw){return h==0?0:(raw-top)*720/h;}
    private JsonObject input(String kind){JsonObject c=new JsonObject();c.addProperty("kind",kind);c.addProperty("shift",isShiftKeyDown());c.addProperty("ctrl",isCtrlKeyDown());c.addProperty("alt",Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU));return c;}
    private void send(JsonObject c){WebScreens.View view=WebScreens.get(tile,true);if(view!=null)view.input(c);}
    private void mouse(String kind,int rawX,int rawY){int xx=x(rawX),yy=y(rawY);if(xx<0||xx>=1280||yy<0||yy>=720)return;JsonObject c=input(kind);c.addProperty("x",xx);c.addProperty("y",yy);send(c);}
    @Override protected void mouseClicked(int x,int y,int button){if(button!=0)return;if(y<26){for(int i=0;i<buttons.length;i++)if(x>=buttons[i]&&x<buttons[i]+sizes[i]){if(i==0)expanded=!expanded;else if(i==1)changeZoom(zoom-25);else if(i==3)changeZoom(zoom+25);else if(i==4)changeZoom(100);else if(i==5)mc.displayGuiScreen(null);return;}return;}pressed=inside(x,y);if(pressed)mouse("down",x,y);}
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
