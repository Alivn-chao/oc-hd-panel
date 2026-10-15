package dev.bec.hdpanel.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bec.hdpanel.ActionPacket;
import dev.bec.hdpanel.ActionReplyPacket;
import dev.bec.hdpanel.BindingModel;
import dev.bec.hdpanel.HdPanelMod;
import dev.bec.hdpanel.PanelTile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Native text boxes provide Ctrl+V/Ctrl+A and caret navigation, including Unicode IME rules. */
public final class BindingGui extends GuiScreen {
    private final PanelGui parent;
    private final PanelTile tile;
    private final JsonArray fields;
    private final String revision;
    private final List<String> values=new ArrayList<String>();
    private final List<GuiTextField> boxes=new ArrayList<GuiTextField>();
    private final List<Integer> indices=new ArrayList<Integer>();
    private int page,rows=1,request;
    private long submitted;
    private String notice="支持 Ctrl+V 粘贴、Ctrl+A 全选；保存不会启动机器";
    public BindingGui(PanelGui parent,PanelTile tile,JsonObject data) {
        this.parent=parent;this.tile=tile;
        revision=data.has("bindingRevision")?data.get("bindingRevision").getAsString():"";
        fields=new JsonParser().parse(data.getAsJsonArray("bindings").toString()).getAsJsonArray();
        for(int i=0;i<fields.size();i++)values.add(fields.get(i).getAsJsonObject().get("value").getAsString());
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    private boolean editable() {
        try {
            JsonObject data=new JsonParser().parse(tile.payload).getAsJsonObject();
            return tile.online&&data.has("bindingEditable")&&data.get("bindingEditable").getAsBoolean();
        }catch(RuntimeException ignored){return false;}
    }
    private void remember() {
        for(int i=0;i<boxes.size();i++)values.set(indices.get(i),boxes.get(i).getText().trim());
    }
    @Override public void initGui() {
        remember();Keyboard.enableRepeatEvents(true);buttonList.clear();boxes.clear();indices.clear();
        rows=Math.max(1,Math.min(8,(height-116)/34));page=Math.min(page,Math.max(0,(fields.size()-1)/rows));
        int labelWidth=Math.min(170,width/3),x=labelWidth+12,w=Math.max(80,width-x-16);
        for(int i=page*rows;i<Math.min(fields.size(),(page+1)*rows);i++) {
            GuiTextField box=new GuiTextField(fontRendererObj,x,45+(i-page*rows)*34,w,20);
            box.setMaxStringLength(160);box.setText(values.get(i));box.setFocused(false);boxes.add(box);indices.add(i);
        }
        if(!boxes.isEmpty())boxes.get(0).setFocused(true);
        int y=height-48;
        buttonList.add(new GuiButton(0,12,y,66,20,"上一页"));
        buttonList.add(new GuiButton(1,82,y,66,20,"下一页"));
        buttonList.add(new GuiButton(2,width-156,y,68,20,"保存绑定"));
        buttonList.add(new GuiButton(3,width-82,y,68,20,"返回"));
    }
    @Override public void onGuiClosed(){remember();Keyboard.enableRepeatEvents(false);}
    @Override public void updateScreen() {
        for(GuiTextField box:boxes)box.updateCursorCounter();
        if(request!=0&&System.currentTimeMillis()-submitted>12000) {request=0;notice="未收到保存回执，请核对 OC 日志后再试";}
        for(Object raw:buttonList) {
            GuiButton b=(GuiButton)raw;
            b.enabled=request==0&&(b.id!=0||page>0)&&(b.id!=1||(page+1)*rows<fields.size())&&(b.id!=2||editable());
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj,"OCHD · 设备地址绑定  "+(page+1)+" / "+Math.max(1,(fields.size()+rows-1)/rows),width/2,15,0xFFFFFF);
        for(int i=0;i<boxes.size();i++) {
            JsonObject field=fields.get(indices.get(i)).getAsJsonObject();
            String label=fontRendererObj.trimStringToWidth(field.get("label").getAsString(),Math.min(170,width/3)-4);
            drawString(fontRendererObj,label,10,51+i*34,0xD7E5EF);boxes.get(i).drawTextBox();
        }
        String status=!editable()?"当前不能保存：停止生产后再绑定":notice;
        drawString(fontRendererObj,fontRendererObj.trimStringToWidth(status,width-24),12,height-20,0xBDDCCF);
        super.drawScreen(mouseX,mouseY,partial);
    }
    @Override protected void mouseClicked(int x,int y,int button) {
        super.mouseClicked(x,y,button);for(GuiTextField box:boxes)box.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char ch,int key) {
        if(key==Keyboard.KEY_ESCAPE) {if(request==0)mc.displayGuiScreen(parent);return;}
        if(request!=0)return;
        if(key==Keyboard.KEY_TAB&&!boxes.isEmpty()) {
            int selected=-1;for(int i=0;i<boxes.size();i++)if(boxes.get(i).isFocused())selected=i;
            for(GuiTextField box:boxes)box.setFocused(false);
            boxes.get((selected+1)%boxes.size()).setFocused(true);return;
        }
        for(GuiTextField box:boxes)if(box.isFocused()){box.textboxKeyTyped(ch,key);return;}
    }
    @Override protected void actionPerformed(GuiButton b) {
        if(request!=0)return;
        if(b.id==3){mc.displayGuiScreen(parent);return;}
        remember();
        if(b.id==0||b.id==1){page+=b.id==0?-1:1;initGui();return;}
        if(b.id!=2||!editable())return;
        StringBuilder payload=new StringBuilder("_revision="+revision);
        for(int i=0;i<fields.size();i++) {
            JsonObject field=fields.get(i).getAsJsonObject();String value=values.get(i);
            if(!BindingModel.valid(field.get("kind").getAsString(),value)) {
                notice="格式不正确："+field.get("label").getAsString();page=i/rows;initGui();return;
            }
            payload.append('\n');payload.append(field.get("key").getAsString()).append('=').append(value);
        }
        if(payload.length()>BindingModel.MAX_PAYLOAD){notice="绑定数据过大，未发送";return;}
        if(!BindingModel.accepts(tile.payload,payload.toString())){notice="绑定字段已变化，请返回重新打开";return;}
        request=new java.util.Random().nextInt(Integer.MAX_VALUE-1)+1;submitted=System.currentTimeMillis();
        HdPanelMod.NETWORK.sendToServer(ActionPacket.bindings(tile,request,payload.toString()));notice="正在保存，等待 OC 回执…";
    }
    public void receive(ActionReplyPacket reply) {
        if(reply.x!=tile.xCoord||reply.y!=tile.yCoord||reply.z!=tile.zCoord||reply.request!=request)return;
        notice=reply.message;if(reply.state!=0)request=0;
    }
}
