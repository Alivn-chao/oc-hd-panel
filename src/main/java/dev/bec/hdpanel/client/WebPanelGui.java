package dev.bec.hdpanel.client;

import com.google.gson.JsonObject;
import dev.bec.hdpanel.PanelTile;
import dev.bec.hdpanel.WallGeometry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Interactive HTML view; the same browser texture is used on the physical screen wall. */
public final class WebPanelGui extends GuiScreen {
    public final PanelTile tile;private int left,top,w,h;private boolean expanded;
    public WebPanelGui(PanelTile tile){this.tile=tile.master();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui(){Keyboard.enableRepeatEvents(true);}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        if(tile.isInvalid()||tile.getWorldObj()!=mc.theWorld||tile.master()!=tile||tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>64||!"web".equals(tile.app)){mc.displayGuiScreen(null);return;}
        drawDefaultBackground();double scale=WallGeometry.guiScale(width,height,expanded);w=(int)(1280*scale);h=(int)(720*scale);left=(width-w)/2;top=(height-h)/2;
        WebScreens.View view=WebScreens.get(tile,true);
        if(view!=null&&view.location()!=null){GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);try{mc.getTextureManager().bindTexture(view.location());GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glColor4f(1,1,1,1);Tessellator t=Tessellator.instance;t.startDrawingQuads();t.setColorOpaque_F(1,1,1);t.addVertexWithUV(left,top+h,0,0,1);t.addVertexWithUV(left+w,top+h,0,1,1);t.addVertexWithUV(left+w,top,0,1,0);t.addVertexWithUV(left,top,0,0,0);t.draw();}finally{GL11.glPopAttrib();}}
        else drawCenteredString(fontRendererObj,view==null?"等待网页显示资源":view.error,width/2,height/2,0xFFFFFF);
        drawCenteredString(fontRendererObj,!tile.online?"OC离线 · 当前数据可能已过期":view!=null&&!view.error.isEmpty()?view.error:"HTML页面 · Ctrl+V粘贴 · F6缩放 · Esc关闭",width/2,height-12,0xD8E1F4);
    }
    private int x(int raw){return w==0?0:(raw-left)*1280/w;}private int y(int raw){return h==0?0:(raw-top)*720/h;}
    private JsonObject input(String kind){JsonObject c=new JsonObject();c.addProperty("kind",kind);c.addProperty("shift",isShiftKeyDown());c.addProperty("ctrl",isCtrlKeyDown());c.addProperty("alt",Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU));return c;}
    private void send(JsonObject c){WebScreens.View view=WebScreens.get(tile,true);if(view!=null)view.input(c);}
    private void mouse(String kind,int rawX,int rawY){int xx=x(rawX),yy=y(rawY);if(xx<0||xx>=1280||yy<0||yy>=720)return;JsonObject c=input(kind);c.addProperty("x",xx);c.addProperty("y",yy);send(c);}
    @Override protected void mouseClicked(int x,int y,int button){if(button==0)mouse("down",x,y);}
    @Override protected void mouseMovedOrUp(int x,int y,int button){if(button==0)mouse("up",x,y);}
    @Override protected void mouseClickMove(int x,int y,int button,long time){mouse("move",x,y);}
    @Override public void handleMouseInput(){super.handleMouseInput();if(Mouse.getEventDX()!=0||Mouse.getEventDY()!=0)mouse("move",Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1);int wheel=Mouse.getEventDWheel();if(wheel!=0){JsonObject c=input("wheel");c.addProperty("x",x(Mouse.getEventX()*width/mc.displayWidth));c.addProperty("y",y(height-Mouse.getEventY()*height/mc.displayHeight-1));c.addProperty("delta",wheel>0?80:-80);send(c);}}
    @Override protected void keyTyped(char ch,int key){
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(null);return;}if(key==Keyboard.KEY_F6){expanded=!expanded;return;}
        if(isCtrlKeyDown()&&key==Keyboard.KEY_V){JsonObject c=input("paste");String text=getClipboardString();c.addProperty("text",text.length()>4096?text.substring(0,4096):text);send(c);return;}
        String code=key==Keyboard.KEY_BACK?"BACK_SPACE":key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER?"ENTER":key==Keyboard.KEY_LCONTROL||key==Keyboard.KEY_RCONTROL?"CONTROL":key==Keyboard.KEY_LSHIFT||key==Keyboard.KEY_RSHIFT?"SHIFT":key==Keyboard.KEY_LMENU||key==Keyboard.KEY_RMENU?"ALT":key==Keyboard.KEY_PRIOR?"PAGE_UP":key==Keyboard.KEY_NEXT?"PAGE_DOWN":key==Keyboard.KEY_SPACE?"SPACE":Keyboard.getKeyName(key);
        if(code!=null&&code.matches("[0-9]"))code="DIGIT"+code;
        JsonObject c=input("key");c.addProperty("key",code==null?"UNDEFINED":code);c.addProperty("character",ch>=32?String.valueOf(ch):"");send(c);
    }
}
