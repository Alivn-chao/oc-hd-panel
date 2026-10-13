package dev.bec.hdpanel.client;

import dev.bec.hdpanel.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.image.BufferedImage;

public class PanelGui extends GuiScreen {
    private final PanelTile tile;
    private final PanelInteraction ui=new PanelInteraction();
    private int left, top, viewWidth, viewHeight, modalVersion=-1, caretWidth;
    private boolean dragging;
    private boolean expanded;
    private String customId="";
    private String seenApp="",seenLayout="";
    private int customValue;
    private String statusKey="";
    private String parsedPayload;
    private JsonObject parsedData=new JsonObject();
    private final ImageTexture modalTexture=new ImageTexture(), statusTexture=new ImageTexture();
    public PanelGui(PanelTile tile) { this.tile = tile.master(); }
    private boolean bec() {return "bec_dashboard".equals(tile.app);}
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() { Keyboard.enableRepeatEvents(true); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); modalTexture.dispose(); statusTexture.dispose(); }
    private JsonObject data() {
        if(!tile.payload.equals(parsedPayload)) {
            parsedPayload=tile.payload;
            try { parsedData=new JsonParser().parse(tile.payload).getAsJsonObject(); }
            catch(RuntimeException ignored) { parsedData=new JsonObject(); }
        }
        return parsedData;
    }
    private boolean enabled(String key) {
        try { JsonObject d=data(); return tile.online && d.has(key) && d.get(key).getAsBoolean(); }
        catch(RuntimeException ignored) { return false; }
    }
    private int rows() {
        JsonObject d=data(); return d.has("fluids") && d.get("fluids").isJsonArray() ? d.getAsJsonArray("fluids").size() : 0;
    }
    private int px(int x) { return viewWidth>0 ? (x-left)*1280/viewWidth : -1; }
    private int py(int y) { return viewHeight>0 ? (y-top)*720/viewHeight : -1; }
    private static boolean inside(int x,int y,int l,int t,int w,int h) { return x>=l && x<l+w && y>=t && y<t+h; }
    private static void outline(int x,int y,int w,int h) {
        drawRect(x,y,x+w,y+2,0xAA466FFF); drawRect(x,y+h-2,x+w,y+h,0xAA466FFF);
        drawRect(x,y,x+2,y+h,0xAA466FFF); drawRect(x+w-2,y,x+w,y+h,0xAA466FFF);
    }
    private void quad(ResourceLocation location,int x,int y,int w,int h) {
        mc.getTextureManager().bindTexture(location); GL11.glColor4f(1,1,1,1);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t=Tessellator.instance; t.startDrawingQuads(); t.setColorOpaque_F(1,1,1);
        t.addVertexWithUV(x,y+h,0,0,1); t.addVertexWithUV(x+w,y+h,0,1,1);
        t.addVertexWithUV(x+w,y,0,1,0); t.addVertexWithUV(x,y,0,0,0); t.draw();
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        if(tile.isInvalid() || tile.master()!=tile || tile.getWorldObj()!=mc.theWorld || tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>64) {
            mc.displayGuiScreen(null); return;
        }
        double scale=WallGeometry.guiScale(width,height,expanded);
        viewWidth=(int)(1280*scale); viewHeight=(int)(720*scale);
        left=(width-viewWidth)/2; top=(height-viewHeight)/2;
        PanelSurfaces.Surface surface=PanelSurfaces.get(tile,true);
        if(!seenApp.equals(tile.app)||!seenLayout.equals(tile.layout)) {
            ui.close();customId="";dragging=false;seenApp=tile.app;seenLayout=tile.layout;
        }
        if(!bec()) {com.google.gson.JsonArray pages=AppModel.parse(tile.layout).getAsJsonArray("pages");if(pages==null||surface.page>=pages.size())surface.page=0;}
        int count=rows(); surface.scroll=PanelInteraction.scroll(surface.scroll,0,count);
        ui.tick(System.currentTimeMillis());
        if(ui.modal==4 && ui.editable!=enabled("configEditable")) { ui.editable=enabled("configEditable"); ui.version++; }
        int x=px(mouseX),y=py(mouseY);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        GL11.glTranslatef(left,top,0); GL11.glScalef(viewWidth/1280F,viewHeight/720F,1);
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING);
        drawRect(-5,-5,1285,725,0xFF344354);drawRect(-2,-2,1282,722,0xFFACB8C7);
        if(surface.location()!=null) quad(surface.location(),0,0,1280,720);
        if(bec()&&surface.page==1) {
            int max=PanelInteraction.maxScroll(count),thumb=count>5?Math.max(30,270*5/count):270;
            int position=315+(max>0?surface.scroll*(270-thumb)/max:0);
            drawRect(1199,position,1209,position+thumb,dragging?0xFF466FFF:0xFF9AAEEF);
        }
        String tooltip="";
        if(ui.modal<0&&!bec()) {
            JsonObject widget=AppModel.buttonAt(tile.layout,surface.page,x,y);
            if(widget!=null) {
                String id=widget.get("id").getAsString();
                if(tile.online&&AppModel.enabled(tile.values,id)&&ui.request==0)outline(widget.get("x").getAsInt(),widget.get("y").getAsInt(),widget.get("w").getAsInt(),widget.get("h").getAsInt());
                tooltip=ui.request!=0?"等待程序处理上一项请求":!tile.online?"应用离线":!AppModel.enabled(tile.values,id)?"当前不可操作":widget.get("label").getAsString();
            }
        }
        if(ui.modal<0&&bec()) {
            if(y>=15 && y<65 && x>=300 && x<924) { int tab=(x-300)/156; outline(300+tab*156,18,144,46); tooltip="点击切换页面"; }
            if(surface.page==0 && inside(x,y,798,565,422,38)) { outline(792,562,430,38); tooltip="查看全部库存，支持鼠标滚轮"; }
            if(surface.page==3 && inside(x,y,64,514,240,48)) { outline(64,514,240,48); tooltip="打开目标输入框；可选预设，也可输入整数"; }
            int action=DashboardPainter.actionAt(x,y);
            if(action>=0) {
                int bx=action==0?820:action==3?925:action==1?1030:1135;
                if(ui.request==0 && tile.online && (action==0 || enabled("controls"))) outline(bx,634,95,40);
                String[] tips={"请求最新快照","暂停现有服务，需要确认","调用原控制器恢复 / 重试入口","调用原界面的样板流程，需要确认"};
                tooltip=ui.request!=0?"正在处理上一项请求":!tile.online?"面板离线":action!=0 && !enabled("controls")?"只读监控：控制按钮不可用":tips[action];
            }
        }
        String status=ui.notice.isEmpty()?tooltip:ui.notice;
        if(!status.isEmpty()) {
            String key=status+":"+(ui.request!=0);
            if(!key.equals(statusKey)) { statusTexture.set(InteractionPainter.status(status,ui.request!=0)); statusKey=key; }
            quad(statusTexture.location,40,635,760,38);
        }
        if(ui.modal>=0) {
            drawRect(0,0,1280,720,0x800F1B32);
            if(modalVersion!=ui.version) { modalTexture.set(InteractionPainter.modal(ui)); modalVersion=ui.version; caretWidth=InteractionPainter.advance(ui.input.substring(0,ui.cursor)); }
            quad(modalTexture.location,280,160,720,400);
            int mx=x-280,my=y-160;
            if(inside(mx,my,420,338,124,42)) outline(700,498,124,42);
            if(inside(mx,my,560,338,132,42) && (ui.modal!=4 || ui.editable)) outline(840,498,132,42);
            if(ui.modal==4) {
                for(int i=0;i<3;i++) if(inside(mx,my,28+i*216,116,200,42)) outline(308+i*216,276,200,42);
                if(ui.focused && !ui.selected && System.currentTimeMillis()%1000<500) drawRect(326+caretWidth,369,328+caretWidth,396,0xFF6688FF);
            }
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
        if(surface.location()==null) drawCenteredString(fontRendererObj,"Preparing HD panel...",width/2,height/2,0xFFFFFF);
        if(!surface.error.isEmpty()) drawCenteredString(fontRendererObj,"Render error: "+surface.error,width/2,height-15,0xFF7777);
    }
    @Override protected void mouseClicked(int x,int y,int button) {
        if(button!=0 || viewWidth<=0) return;
        int xx=px(x),yy=py(y);
        if(ui.modal>=0) {
            int mx=xx-280,my=yy-160;
            if(inside(mx,my,420,338,124,42)) { ui.close(); return; }
            if(inside(mx,my,560,338,132,42)) { confirm(); return; }
            if(ui.modal==4) {
                for(int i=0;i<3;i++) if(inside(mx,my,28+i*216,116,200,42)) { ui.preset(new String[]{"144000","1000000","5000000"}[i]); return; }
                ui.focused=inside(mx,my,28,194,664,56); ui.version++;
                if(ui.focused) { ui.selected=false; ui.cursor=ui.input.length(); }
            }
            return;
        }
        PanelSurfaces.Surface surface=PanelSurfaces.get(tile,true);
        if(!bec()) {
            int tab=AppPainter.tabAt(tile.layout,xx,yy);if(tab>=0){surface.page=tab;return;}
            JsonObject widget=AppModel.buttonAt(tile.layout,surface.page,xx,yy);
            if(widget==null||!tile.online||ui.request!=0||!AppModel.enabled(tile.values,widget.get("id").getAsString()))return;
            customId=widget.get("id").getAsString();JsonObject v=AppModel.value(tile.values,customId);
            customValue=widget.get("type").getAsString().equals("toggle")?((v.has("active")&&v.get("active").getAsBoolean())?0:1):0;
            String confirmation=widget.get("confirm").getAsString();
            if(confirmation.isEmpty())submit(5,customValue);
            else {ui.customLabel=widget.get("label").getAsString();ui.customConfirm=confirmation;ui.open(5,"",true);}
            return;
        }
        if(yy>=15 && yy<65 && xx>=300 && xx<924) { surface.page=(xx-300)/156; return; }
        if(surface.page==0 && inside(xx,yy,798,565,422,38)) { surface.page=1; return; }
        if(surface.page==1 && inside(xx,yy,1194,315,22,270)) { dragging=true; dragScroll(yy,surface); return; }
        if(surface.page==3 && inside(xx,yy,64,514,240,48)) {
            String target="144000";
            try { if(data().has("configTarget")) target=Long.toString(data().get("configTarget").getAsLong()); } catch(RuntimeException ignored) { }
            if(ui.request==0) ui.open(4,target,enabled("configEditable")); return;
        }
        int action=DashboardPainter.actionAt(xx,yy);
        if(action<0 || !tile.online || ui.request!=0 || (action!=0 && !enabled("controls"))) return;
        if(action==0) submit(0,0); else ui.open(action,"",true);
    }
    private void confirm() {
        if(ui.request!=0) return;
        if(ui.modal==5) {
            if(!tile.online||!AppModel.actionable(tile.layout,tile.values,customId,customValue)){ui.notice="控件已禁用或应用离线";ui.version++;return;}
            ui.close();submit(5,customValue);return;
        }
        if(ui.modal==4 && !enabled("configEditable")) { ui.notice="先暂停服务；只读监控不能保存"; ui.version++; return; }
        if(ui.modal!=4 && !enabled("controls")) { ui.notice="控制接口当前不可用"; ui.version++; return; }
        Integer value=ui.modal==4?PanelInteraction.target(ui.input):0;
        if(value==null) { ui.notice="请输入 1–2147483647 之间的整数 mB"; ui.version++; return; }
        int action=ui.modal; ui.close(); submit(action,value);
    }
    private void submit(int action,int value) {
        int request=new java.util.Random().nextInt(Integer.MAX_VALUE-1)+1;
        ui.pending(request,System.currentTimeMillis()); HdPanelMod.NETWORK.sendToServer(action==5?new ActionPacket(tile,request,value,customId):new ActionPacket(tile,action,request,value));
    }
    public void receive(ActionReplyPacket reply) {
        if(reply.x==tile.xCoord && reply.y==tile.yCoord && reply.z==tile.zCoord) ui.reply(reply.request,reply.state,reply.message);
    }
    @Override public void handleMouseInput() {
        super.handleMouseInput(); int wheel=Mouse.getEventDWheel(); if(wheel==0 || ui.modal>=0) return;
        int x=px(Mouse.getEventX()*width/mc.displayWidth),y=py(height-Mouse.getEventY()*height/mc.displayHeight-1);
        PanelSurfaces.Surface s=PanelSurfaces.get(tile,true);
        if(bec()&&s.page==1 && inside(x,y,40,260,1200,348)) s.scroll=PanelInteraction.scroll(s.scroll,wheel>0?-1:1,rows());
    }
    private void dragScroll(int y,PanelSurfaces.Surface s) {
        int count=rows(),thumb=count>5?Math.max(30,270*5/count):270,track=270-thumb,max=PanelInteraction.maxScroll(count);
        s.scroll=track<=0?0:PanelInteraction.scroll(0,(int)Math.round((y-315-thumb/2.0)*max/track),count);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long time) { if(dragging && button==0) dragScroll(py(y),PanelSurfaces.get(tile,true)); }
    @Override protected void mouseMovedOrUp(int x,int y,int button) { if(button==0) dragging=false; }
    @Override protected void keyTyped(char character,int key) {
        if(key==Keyboard.KEY_F6&&ui.modal<0){expanded=!expanded;return;}
        if(ui.modal<0) { super.keyTyped(character,key); return; }
        if(key==Keyboard.KEY_ESCAPE) { ui.close(); return; }
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER) { confirm(); return; }
        if(ui.modal!=4) return;
        if(key==Keyboard.KEY_TAB) { ui.focused=!ui.focused; ui.version++; return; }
        if(!ui.focused) return;
        if(isCtrlKeyDown()) {
            if(key==Keyboard.KEY_A) { ui.selected=true; ui.version++; }
            else if(key==Keyboard.KEY_C && ui.selected) setClipboardString(ui.input);
            else if(key==Keyboard.KEY_V) ui.replace(getClipboardString().trim());
            return;
        }
        if(key==Keyboard.KEY_BACK) ui.erase(true); else if(key==Keyboard.KEY_DELETE) ui.erase(false);
        else if(key==Keyboard.KEY_LEFT) ui.move(ui.selected?0:ui.cursor-1);
        else if(key==Keyboard.KEY_RIGHT) ui.move(ui.selected?ui.input.length():ui.cursor+1);
        else if(key==Keyboard.KEY_HOME) ui.move(0); else if(key==Keyboard.KEY_END) ui.move(ui.input.length());
        else if(character>='0' && character<='9') ui.replace(String.valueOf(character));
    }
    private final class ImageTexture {
        DynamicTexture texture; ResourceLocation location;
        void set(BufferedImage image) {
            if(texture==null) { texture=new DynamicTexture(image.getWidth(),image.getHeight()); location=mc.getTextureManager().getDynamicTextureLocation("ochdpanel-overlay",texture); }
            image.getRGB(0,0,image.getWidth(),image.getHeight(),texture.getTextureData(),0,image.getWidth()); texture.updateDynamicTexture();
            mc.getTextureManager().bindTexture(location);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        }
        void dispose() { if(location!=null) mc.getTextureManager().deleteTexture(location); texture=null; location=null; }
    }
}
