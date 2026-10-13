package dev.bec.hdpanel.client;

/** Client-local interaction state. Pointer movement never publishes an OC snapshot. */
public final class PanelInteraction {
    public int modal=-1, cursor, request;
    public boolean selected, focused, editable;
    public String input="144000", notice="";
    public long deadline;
    public int version;
    public void open(int action, String target, boolean canEdit) {
        modal=action; editable=canEdit; input=target; cursor=input.length(); selected=false;
        focused=action==4; notice=""; version++;
    }
    public void close() { modal=-1; focused=false; version++; }
    public static Integer target(String input) {
        if (input==null || !input.matches("[0-9]{1,10}")) return null;
        try { long n=Long.parseLong(input); return n>0 && n<=Integer.MAX_VALUE ? (int)n : null; }
        catch (NumberFormatException ignored) { return null; }
    }
    public void replace(String text) {
        if (text==null || !text.matches("[0-9]*")) { notice="只接受整数 mB，可使用 Ctrl+A / Ctrl+V"; version++; return; }
        String next=selected ? text : input.substring(0,cursor)+text+input.substring(cursor);
        if (next.length()>10) { notice="最多输入 10 位数字"; version++; return; }
        input=next; cursor=selected ? text.length() : cursor+text.length(); selected=false; notice=""; version++;
    }
    public void preset(String value) { input=value; cursor=value.length(); selected=false; focused=true; notice=""; version++; }
    public void erase(boolean backwards) {
        if (selected) { input=""; cursor=0; selected=false; }
        else if (backwards && cursor>0) { input=input.substring(0,cursor-1)+input.substring(cursor); cursor--; }
        else if (!backwards && cursor<input.length()) input=input.substring(0,cursor)+input.substring(cursor+1);
        notice=""; version++;
    }
    public void move(int to) { cursor=Math.max(0,Math.min(input.length(),to)); selected=false; version++; }
    public void pending(int id, long now) { request=id; deadline=now+12000; notice="正在提交请求…"; version++; }
    public void reply(int id, int state, String message) {
        if (id!=request || request==0) return;
        notice=message; if (state!=0) request=0; version++;
    }
    public void tick(long now) {
        if (request!=0 && now>deadline) { request=0; notice="回执等待超时；请核对状态，结果尚未确认"; version++; }
    }
    public static int maxScroll(int rows) { return Math.max(0,rows-5); }
    public static int scroll(int offset, int delta, int rows) {
        return Math.max(0,Math.min(maxScroll(rows),offset+delta));
    }
}
