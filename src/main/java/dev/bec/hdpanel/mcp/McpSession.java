package dev.bec.hdpanel.mcp;

import com.google.gson.*;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import java.util.*;

public final class McpSession {
    final McpBox box;
    String id() { return box.node().address()+":"+host.machine().node().address(); }
    final MachineHost host;
    final String owner;
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<String, Job>();
    private long heartbeat;
    static final class Job {
        final String id, op; final JsonObject args; final long created = System.currentTimeMillis();
        String state = "queued"; JsonElement result;
        Job(String id, String op, JsonObject args) { this.id=id; this.op=op; this.args=args; }
    }
    McpSession(MachineHost host, McpBox box) { this.host=host;this.owner=box.owner;this.box=box; }
    synchronized JsonObject submit(String id, String op, JsonObject args) {
        prune();
        if (jobs.containsKey(id)) return job(id);
        if (System.currentTimeMillis()-heartbeat > 10000) throw new IllegalStateException("OpenOS agent offline: install_agent then start computer");
        int active=0; for(Job j:jobs.values()) if(!j.state.equals("done")) active++;
        if(active>=McpLimits.QUEUE) throw new IllegalStateException("computer request queue full");
        jobs.put(id, new Job(id,op,args)); return job(id);
    }
    private void prune() {
        Iterator<Job> it=jobs.values().iterator();
        while(it.hasNext()) { Job j=it.next(); if(System.currentTimeMillis()-j.created>300000) it.remove(); }
        while(jobs.size()>=32) { Iterator<Job> old=jobs.values().iterator(); boolean removed=false; while(old.hasNext()) if(old.next().state.equals("done")) { old.remove(); removed=true; break; } if(!removed)break; }
    }
    synchronized JsonObject job(String id) {
        Job j=jobs.get(id); if(j==null)throw new IllegalArgumentException("unknown or expired job; do not retry mutations automatically");
        JsonObject out=new JsonObject(); out.addProperty("job",id);out.addProperty("state",j.state);if(j.result!=null)out.add("result",j.result);return out;
    }
    synchronized boolean online() { return System.currentTimeMillis()-heartbeat<10000; }
    private void check(Context context) { if(context!=host.machine() || !box.connected(host.machine()))throw new IllegalArgumentException("only the computer containing this card may use its agent"); }
    @Callback(direct = true, limit = 4, doc = "function():string,string,table -- internal agent request")
    public synchronized Object[] next(Context context, Arguments args) {
        check(context); heartbeat=System.currentTimeMillis();
        for(Job j:jobs.values()) if(j.state.equals("queued")) { j.state="running"; return new Object[]{j.id,j.op,new Gson().fromJson(j.args, Map.class)}; }
        return new Object[]{null};
    }
    @Callback(direct = true, limit = 4, doc = "function(id:string,result:string):boolean -- complete agent request")
    public synchronized Object[] complete(Context context, Arguments args) {
        check(context);String id=args.checkString(0), result=McpLimits.bounded(args.checkString(1),65536);
        Job j=jobs.get(id);if(j==null || !j.state.equals("running"))return new Object[]{false};
        j.result=new JsonParser().parse(result);j.state="done"; return new Object[]{true};
    }
}
