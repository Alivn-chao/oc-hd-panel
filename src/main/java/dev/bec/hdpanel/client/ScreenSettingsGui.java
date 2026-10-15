package dev.bec.hdpanel.client;

import dev.bec.hdpanel.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Local display controls, validated and persisted by the server. Works without RF or Lua. */
public final class ScreenSettingsGui extends GuiScreen {
    private final PanelTile tile;
    private int left,top,level;private boolean enabled,dragging;private long lastSend;
    public ScreenSettingsGui(PanelTile tile){this.tile=tile.master();level=this.tile.brightness;enabled=this.tile.screenEnabled;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){left=(width-260)/2;top=(height-210)/2;}
    private boolean inside(int x,int y,int rx,int ry,int w,int h){return x>=left+rx&&x<left+rx+w&&y>=top+ry&&y<top+ry+h;}
    private void send(){HdPanelMod.NETWORK.sendToServer(new PanelSettingsPacket(tile,enabled,level));lastSend=System.currentTimeMillis();}
    private void slider(int x){level=PanelPower.brightness(10+(x-left-22)*90/216);}
    public void drawScreen(int x,int y,float partial){
        if(tile.isInvalid()||tile.getWorldObj()!=mc.theWorld||tile.master()!=tile||tile.distanceSq(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ)>64){mc.displayGuiScreen(null);return;}
        drawDefaultBackground();drawRect(left-3,top-3,left+263,top+213,0xFF111722);drawRect(left,top,left+260,top+210,0xFF56647D);drawRect(left+3,top+3,left+257,top+207,0xFF272F40);
        drawString(fontRendererObj,"OC HD / 屏幕设置",left+16,top+15,0xB7D6ED);
        drawString(fontRendererObj,"屏幕墙 "+tile.wallWidth+" × "+tile.wallHeight,left+16,top+34,0x8290A7);
        drawRect(left+16,top+51,left+244,top+76,inside(x,y,16,51,228,25)?0xFF3B4D69:0xFF202938);
        drawString(fontRendererObj,"屏幕开关："+(enabled?"开启":"关闭"),left+24,top+60,enabled?0x22BDB8:0x8290A7);
        drawString(fontRendererObj,"亮度："+level+"%",left+16,top+88,0xB7D6ED);
        drawRect(left+22,top+110,left+238,top+117,0xFF111722);
        int thumb=left+22+(level-10)*216/90;drawRect(left+22,top+110,thumb,top+117,0xFF22BDB8);drawRect(thumb-3,top+106,thumb+4,top+121,0xFFB7D6ED);
        drawString(fontRendererObj,!tile.screenEnabled?"状态：已关闭":tile.powered?"状态：供电正常":"状态：能量不足 / 未连接供电",left+16,top+132,tile.powered?0x22BDB8:0xD6A05F);
        drawString(fontRendererObj,"本机缓存 "+tile.rfStored+" / "+tile.rfCapacity+" RF",left+16,top+148,0x8290A7);
        drawString(fontRendererObj,String.format(java.util.Locale.ROOT,"OC网络 %.1f / %.1f",tile.ocStored,tile.ocCapacity),left+16,top+162,0x8290A7);
        drawString(fontRendererObj,"等效耗电 "+(tile.screenEnabled?tile.rfPerTick:0)+" RF/t",left+16,top+178,0x8290A7);
        drawString(fontRendererObj,"RF / EU / OC / AE · Esc 返回",left+16,top+194,0x8290A7);
        if(!dragging&&System.currentTimeMillis()-lastSend>1200){enabled=tile.screenEnabled;level=tile.brightness;}
    }
    protected void mouseClicked(int x,int y,int button){if(button!=0)return;if(inside(x,y,16,51,228,25)){enabled=!enabled;send();}else if(inside(x,y,16,103,228,23)){dragging=true;slider(x);}}
    protected void mouseClickMove(int x,int y,int button,long time){if(dragging&&button==0)slider(x);}
    protected void mouseMovedOrUp(int x,int y,int button){if(dragging&&button==0){slider(x);dragging=false;send();}}
    public void onGuiClosed(){if(dragging)send();}
    protected void keyTyped(char ch,int key){if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(null);}
}
