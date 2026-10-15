package dev.bec.hdpanel;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebViewportTest {
    @Test public void frameTabsAndGlassFitCommonGuiScales(){
        for(int[] size:new int[][]{{320,240},{640,360},{960,540},{1920,1080},{2560,1080}})for(boolean full:new boolean[]{false,true}){
            WebViewport v=new WebViewport(size[0],size[1],full);assertTrue(v.tabTop>=0);assertTrue(v.frameLeft>=0);assertTrue(v.top+v.height+24*v.scale<=size[1]);
            assertTrue(v.tabLeft(5)+WebViewport.TAB_WIDTHS[5]<=size[0]);assertEquals(640,v.pageX(v.left+v.width/2),5);assertEquals(360,v.pageY(v.top+v.height/2),5);
        }
    }
    @Test public void tabClicksNeverCountAsBrowserInput(){WebViewport v=new WebViewport(960,540,true);for(int i=0;i<6;i++){int x=v.tabLeft(i)+3,y=v.tabTop+8;assertEquals(i,v.tabAt(x,y));assertFalse(v.inside(x,y));}assertEquals(-1,v.tabAt(v.left+20,v.top+20));}
}
