package dev.bec.hdweb;

import java.awt.Component;
import java.awt.event.KeyEvent;

/** JCEF on Windows reads the AWT event's scancode field through JNI.
 * Constructed KeyEvents have zero there, so editing/navigation became key 0.
 * A concrete event field supplies the real Set-1 scan without module reflection.
 */
final class BrowserKeyEvent extends KeyEvent {
    public final long scancode;
    BrowserKeyEvent(Component source,int id,long when,int modifiers,int code,char text,int lwjglScan) {
        super(source,id,when,modifiers,code,text);
        scancode=lwjglScan>0&&lwjglScan<256?(lwjglScan&127)|(lwjglScan>=128?0xe000:0):scan(code);
    }
    private static int scan(int code) {
        if(code>=VK_A&&code<=VK_Z)return new int[]{30,48,46,32,18,33,34,35,23,36,37,38,50,49,24,25,16,19,31,20,22,47,17,45,21,44}[code-VK_A];
        if(code>=VK_0&&code<=VK_9)return new int[]{11,2,3,4,5,6,7,8,9,10}[code-VK_0];
        switch(code){
            case VK_BACK_SPACE:return 14;case VK_DELETE:return 0xe053;
            case VK_TAB:return 15;case VK_ENTER:return 28;case VK_ESCAPE:return 1;
            case VK_LEFT:return 0xe04b;case VK_RIGHT:return 0xe04d;case VK_UP:return 0xe048;case VK_DOWN:return 0xe050;
            case VK_HOME:return 0xe047;case VK_END:return 0xe04f;case VK_PAGE_UP:return 0xe049;case VK_PAGE_DOWN:return 0xe051;
            case VK_SPACE:return 57;case VK_SHIFT:return 42;case VK_CONTROL:return 29;case VK_ALT:return 56;
            case VK_MINUS:return 12;case VK_EQUALS:return 13;case VK_OPEN_BRACKET:return 26;case VK_CLOSE_BRACKET:return 27;
            case VK_BACK_SLASH:return 43;case VK_SEMICOLON:return 39;case VK_QUOTE:return 40;
            case VK_COMMA:return 51;case VK_PERIOD:return 52;case VK_SLASH:return 53;case VK_BACK_QUOTE:return 41;
            default:return 0;
        }
    }
}
