package dev.bec.hdpanel.client;

import com.google.gson.*;
import dev.bec.hdpanel.*;
import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Embedded in the existing HD page; text boxes only provide native clipboard/caret editing. */
public final class BindingGui extends GuiScreen {
    private final PanelGui parent;
    private final PanelTile tile;
    private final JsonArray fields;
    private String revision;
    private final List<String> values=new ArrayList<String>();
    private final List<GuiTextField> boxes=new ArrayList<GuiTextField>();
    private int page,request,focus=-1,dropdown=-1;
    private long submitted,paintTick=-1;
    private boolean dirty,redraw=true,draftPending;
    private String notice="支持 Ctrl+V；未填完整可暂存，返回时自动暂存草稿";
    private DynamicTexture texture;
    private ResourceLocation location;
    public BindingGui(PanelGui parent,PanelTile tile,JsonObject data) {
        this.parent=parent;this.tile=tile;
        revision=data.get("bindingRevision").getAsString();
        fields=new JsonParser().parse(data.getAsJsonArray("bindings").toString()).getAsJsonArray();
        for(JsonElement raw:fields)values.add(raw.getAsJsonObject().get("value").getAsString());
    }
    private JsonObject data(){try{return new JsonParser().parse(tile.payload).getAsJsonObject();}catch(RuntimeException e){return new JsonObject();}}
    private boolean allowed(boolean draft){JsonObject d=data();String key=draft?"bindingDraftEditable":"bindingEditable";return tile.online&&d.has(key)&&d.get(key).getAsBoolean();}
    private void remember(){for(int i=0;i<boxes.size();i++)values.set(page*8+i,boxes.get(i).getText());}
    @Override public void initGui(){
        boxes.clear();focus=-1;dropdown=-1;
        for(int i=page*8;i<Math.min(fields.size(),page*8+8);i++){
            GuiTextField box=new GuiTextField(fontRendererObj,0,0,760,30);
            box.setMaxStringLength(160);box.setText(values.get(i));box.setFocused(false);boxes.add(box);
        }
        redraw=true;
    }
    public void tick(){
        for(GuiTextField box:boxes)box.updateCursorCounter();
        if(request!=0&&System.currentTimeMillis()-submitted>12000){request=0;notice="未收到回执，草稿仍留在当前页面；请重试暂存";redraw=true;}
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        long blink=System.currentTimeMillis()/500;
        if(redraw||blink!=paintTick){
            remember();int cursor=focus>=0?boxes.get(focus).getCursorPosition():0;
            int selection=focus>=0?boxes.get(focus).getSelectionEnd():0;
            java.awt.image.BufferedImage image=BindingPainter.paint(fields,values,page,focus,cursor,selection,blink%2==0,dropdown,notice,request!=0);
            if(texture==null){texture=new DynamicTexture(1920,1080);location=mc.getTextureManager().getDynamicTextureLocation("ochd-bindings-page",texture);}
            image.getRGB(0,0,1920,1080,texture.getTextureData(),0,1920);texture.updateDynamicTexture();paintTick=blink;redraw=false;
        }
        mc.getTextureManager().bindTexture(location);GL11.glColor4f(1,1,1,1);GL11.glEnable(GL11.GL_TEXTURE_2D);
        Tessellator t=Tessellator.instance;t.startDrawingQuads();t.setColorOpaque_F(1,1,1);
        t.addVertexWithUV(0,720,0,0,1);t.addVertexWithUV(1280,720,0,1,1);t.addVertexWithUV(1280,0,0,1,0);t.addVertexWithUV(0,0,0,0,0);t.draw();
    }
    @Override protected void mouseClicked(int x,int y,int button){
        if(button!=0||request!=0)return;
        if(y>=15&&y<65&&x>=300&&x<924){parent.bindingTab((x-300)/156);return;}
        if(y>=634&&y<674){
            if(x>=40&&x<135&&page>0){remember();page--;initGui();return;}
            if(x>=150&&x<245&&(page+1)*8<fields.size()){remember();page++;initGui();return;}
            if(x>=790&&x<920){submit(true);return;}
            if(x>=935&&x<1065){submit(false);return;}
            if(x>=1080&&x<1230){parent.closeBindings();return;}
        }
        int row=(y-145)/48;
        if(y<145||row<0||row>=boxes.size()||x<390||x>1215){dropdown=-1;return;}
        JsonObject f=fields.get(page*8+row).getAsJsonObject();String kind=f.get("kind").getAsString();
        if("side".equals(kind)){
            focus=-1;for(GuiTextField b:boxes)b.setFocused(false);
            if(dropdown==row){int selected=Math.max(0,Math.min(5,(x-390)/137));boxes.get(row).setText(Integer.toString(BindingPainter.SIDES[selected]));dirty=true;dropdown=-1;}
            else dropdown=row;
        }else{
            dropdown=-1;for(GuiTextField b:boxes)b.setFocused(false);focus=row;boxes.get(row).setFocused(true);
            boxes.get(row).setCursorPositionEnd();
        }
        remember();redraw=true;
    }
    @Override protected void keyTyped(char ch,int key){
        if(key==Keyboard.KEY_ESCAPE){parent.closeBindings();return;}
        if(request!=0)return;
        if(key==Keyboard.KEY_TAB&&!boxes.isEmpty()){
            for(GuiTextField b:boxes)b.setFocused(false);focus=(focus+1)%boxes.size();boxes.get(focus).setFocused(true);dropdown=-1;redraw=true;return;
        }
        if(focus>=0&&!"side".equals(fields.get(page*8+focus).getAsJsonObject().get("kind").getAsString())){
            String old=boxes.get(focus).getText();boxes.get(focus).textboxKeyTyped(ch,key);
            if(!old.equals(boxes.get(focus).getText()))dirty=true;remember();redraw=true;
        }
    }
    private void submit(boolean draft){
        if(request!=0||!allowed(draft)){notice="当前不能保存，请确认 OC 配置服务在线";redraw=true;return;}
        remember();StringBuilder payload=new StringBuilder("_revision="+revision);
        for(int i=0;i<fields.size();i++){
            JsonObject field=fields.get(i).getAsJsonObject();String value=values.get(i);
            if(!(draft?BindingModel.draftValue(field.get("kind").getAsString(),value):BindingModel.valid(field.get("kind").getAsString(),value))){
                notice="格式未完成，请先暂存草稿："+field.get("label").getAsString();redraw=true;return;
            }
            payload.append('\n').append(field.get("key").getAsString()).append('=').append(value);
        }
        if(!BindingModel.accepts(tile.payload,payload.toString(),draft)){notice="页面版本已变化，请重新打开；当前输入尚在此页";redraw=true;return;}
        request=new Random().nextInt(Integer.MAX_VALUE-1)+1;submitted=System.currentTimeMillis();draftPending=draft;
        HdPanelMod.NETWORK.sendToServer(ActionPacket.bindings(tile,request,payload.toString(),draft));
        notice=draft?"正在暂存草稿…":"正在校验并应用绑定…";redraw=true;
    }
    public void leave(){if(dirty&&request==0&&allowed(true))submit(true);dispose();}
    public void dispose(){if(location!=null)mc.getTextureManager().deleteTexture(location);texture=null;location=null;redraw=true;}
    public void receive(ActionReplyPacket reply){
        if(reply.x!=tile.xCoord||reply.y!=tile.yCoord||reply.z!=tile.zCoord||reply.request!=request)return;
        notice=reply.message;int mark=notice.indexOf("] ");
        if(notice.startsWith("[rev:")&&mark>5){revision=notice.substring(5,mark);notice=notice.substring(mark+2);}
        if(reply.state!=0){request=0;if(reply.state==1)dirty=false;}
        redraw=true;
    }
}
