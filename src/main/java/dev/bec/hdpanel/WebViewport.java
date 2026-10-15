package dev.bec.hdpanel;

/** GUI geometry in scaled Minecraft pixels; tabs sit above the original metal frame. */
public final class WebViewport {
    public static final int[] TAB_WIDTHS={34,28,42,28,34,34};
    public final double scale;
    public final int frameLeft,frameTop,left,top,width,height,tabTop;
    public WebViewport(int screenWidth,int screenHeight,boolean expanded){
        double roomW=screenWidth-(expanded?16:64),roomH=screenHeight-(expanded?56:96);
        scale=Math.max(.01,Math.min(roomW/1328.0,roomH/768.0));
        frameLeft=(int)((screenWidth-1328*scale)/2);frameTop=34+Math.max(0,(int)((screenHeight-56-768*scale)/2));
        left=(int)(frameLeft+24*scale);top=(int)(frameTop+24*scale);width=(int)(1280*scale);height=(int)(720*scale);tabTop=frameTop-26;
    }
    public int tabLeft(int index){int x=frameLeft+2;for(int i=0;i<index;i++)x+=TAB_WIDTHS[i]+4;return x;}
    public int tabAt(int x,int y){if(y<tabTop-2||y>=frameTop)return -1;for(int i=0;i<TAB_WIDTHS.length;i++)if(x>=tabLeft(i)&&x<tabLeft(i)+TAB_WIDTHS[i])return i;return -1;}
    public boolean inside(int x,int y){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    public int pageX(int x){return width==0?0:(x-left)*1280/width;}
    public int pageY(int y){return height==0?0:(y-top)*720/height;}
}
