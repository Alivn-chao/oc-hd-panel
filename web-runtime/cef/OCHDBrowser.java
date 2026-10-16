package org.cef.browser;

import org.cef.*;
import org.cef.handler.*;
import org.cef.callback.CefDragData;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Windowless CEF surface without JOGL or an extra visible browser window.
 * Kept in this package because upstream's native browser base is package-private. */
public final class OCHDBrowser extends CefBrowser_N implements CefRenderHandler {
    public interface Paint {void accept(ByteBuffer pixels,int width,int height);}
    private final Canvas inputSource=new Canvas();
    private final Paint paint;
    public OCHDBrowser(CefClient client,Paint paint) {
        super(client,"about:blank",null,null,null,new CefBrowserSettings());this.paint=paint;
    }
    @Override public void createImmediately(){createBrowser(getClient(),0,getUrl(),true,false,null,null);}
    @Override public Component getUIComponent(){return inputSource;}
    @Override public CefRenderHandler getRenderHandler(){return this;}
    @Override protected CefBrowser_N createDevToolsBrowser(CefClient c,String u,CefRequestContext r,CefBrowser_N p,Point i){throw new UnsupportedOperationException();}
    @Override public Rectangle getViewRect(CefBrowser b){return new Rectangle(0,0,1280,720);}
    @Override public boolean getScreenInfo(CefBrowser b,CefScreenInfo s){s.Set(1.5,32,8,false,new Rectangle(0,0,1280,720),new Rectangle(0,0,1280,720));return true;}
    @Override public Point getScreenPoint(CefBrowser b,Point p){return p;}
    @Override public void onPaint(CefBrowser b,boolean popup,Rectangle[] dirty,ByteBuffer pixels,int w,int h){if(!popup)paint.accept(pixels,w,h);}
    @Override public void onPopupShow(CefBrowser b,boolean show){}
    @Override public void onPopupSize(CefBrowser b,Rectangle r){}
    @Override public void addOnPaintListener(Consumer<CefPaintEvent> l){}
    @Override public void setOnPaintListener(Consumer<CefPaintEvent> l){}
    @Override public void removeOnPaintListener(Consumer<CefPaintEvent> l){}
    @Override public boolean onCursorChange(CefBrowser b,int cursor){return true;}
    @Override public boolean startDragging(CefBrowser b,CefDragData d,int mask,int x,int y){return false;}
    @Override public void updateDragCursor(CefBrowser b,int operation){}
    @Override public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution){throw new UnsupportedOperationException();}
    public void mouse(MouseEvent e){sendMouseEvent(e);}
    public void wheel(MouseWheelEvent e){sendMouseWheelEvent(e);}
    public void key(KeyEvent e){sendKeyEvent(e);}
}
