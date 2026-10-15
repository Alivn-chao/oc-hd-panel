package dev.bec.hdpanel;

/** Recovered facing and joined-edge rules from the supplied alpha4 binary. */
public final class PanelFacing {
    public static int placed(float yaw){int direction=(int)Math.floor(yaw/90D+.5D)&3;return(2-direction)&3;}
    public static int front(int meta){return new int[]{3,5,2,4}[meta&3];}
    public static int edges(int col,int row,int width,int height){return(col==0?1:0)|(col==width-1?2:0)|(row==height-1?4:0)|(row==0?8:0);}
}
