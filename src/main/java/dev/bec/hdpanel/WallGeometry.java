package dev.bec.hdpanel;

/** Pure geometry shared by world rendering and tests. Local coordinates are measured from bottom-left. */
public final class WallGeometry {
    public final double left,bottom,width,height;
    public WallGeometry(int columns,int rows) {
        double w=columns-.1875,h=rows-.1875;
        width=Math.min(w,h*16/9.0);height=width*9/16.0;
        left=(columns-width)/2;bottom=(rows-height)/2;
    }
    public double[] slice(int column,int row) {
        double l=Math.max(column,left),r=Math.min(column+1,left+width),b=Math.max(row,bottom),t=Math.min(row+1,bottom+height);
        if(l>=r||b>=t)return null;
        return new double[]{l-column-.5,b-row-.5,r-column-.5,t-row-.5,(l-left)/width,1-(t-bottom)/height,(r-left)/width,1-(b-bottom)/height};
    }
    public static double guiScale(int width,int height,boolean expanded) {
        return Math.max(.01,Math.min((expanded?width-16:width*.45)/1328.0,(expanded?height-32:height*.56)/768.0));
    }
}
