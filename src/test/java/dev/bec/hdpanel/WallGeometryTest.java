package dev.bec.hdpanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class WallGeometryTest {
    @Test public void worldSlicesCoverExactlyOneAspectCorrectImage() {
        for(int width=1;width<=8;width++)for(int height=1;height<=6;height++) {
            WallGeometry g=new WallGeometry(width,height);double area=0;
            for(int col=0;col<width;col++)for(int row=0;row<height;row++) {
                double[] s=g.slice(col,row);if(s==null)continue;area+=(s[2]-s[0])*(s[3]-s[1]);
                for(int i=4;i<8;i++)assertTrue(s[i]>=-1E-9&&s[i]<=1+1E-9);
            }
            assertEquals(g.width*g.height,area,1E-9);assertEquals(16/9.0,g.width/g.height,1E-9);
        }
    }
    @Test public void defaultWindowLeavesMostOfWorldVisibleAndExpansionIsOptional() {
        double small=WallGeometry.guiScale(1920,1080,false),large=WallGeometry.guiScale(1920,1080,true);
        assertEquals(864,1280*small,.001);assertEquals(486,720*small,.001);assertTrue(large>small*2);
        assertTrue(1280*WallGeometry.guiScale(320,240,false)<=320*.45+.001);
    }
    @Test public void onlyCompleteRectanglesWithOneControllerMerge() {
        assertTrue(WallAssembly.rectangle(5,3,15,0));assertTrue(WallAssembly.rectangle(8,6,48,1));
        assertFalse(WallAssembly.rectangle(5,3,14,0));assertFalse(WallAssembly.rectangle(9,1,9,0));assertFalse(WallAssembly.rectangle(5,3,15,2));
        assertEquals(3,WallAssembly.u(0,3,7));assertEquals(-7,WallAssembly.u(1,3,7));assertEquals(-3,WallAssembly.u(2,3,7));assertEquals(7,WallAssembly.u(3,3,7));
    }
}
