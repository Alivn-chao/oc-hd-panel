"""Real WebKit stream: static-frame suppression, OC updates, pause, and resume."""
import time, queue
from benchmark_stream import ROOT, launch, send, event, close
p,q=launch(ROOT/'build/web-host.jar')
def frames(seconds):
    count=0;end=time.perf_counter()+seconds
    while time.perf_counter()<end:
        try:_,typ,obj=q.get(timeout=min(.1,max(.001,end-time.perf_counter())))
        except queue.Empty:continue
        if typ in (1,3):count+=1
        if typ==-1 or (typ==2 and obj.get('type')=='error'):raise RuntimeError(obj)
    return count
try:
    event(q,'started');send(p,{'type':'load','package':{'entry':'index.html','files':{'index.html':"<html><body style='background:#111722'><script>OC.onData(function(d){document.body.style.background=d.color||'#111722';});</script></body></html>"}}});event(q,'ready')
    send(p,{'type':'stream','fps':60});assert frames(.6)>=1
    assert frames(.6)==0,'identical static frames were resent'
    send(p,{'type':'data','data':{'color':'#22bdb8'}});assert frames(.5)>=1,'OC data did not repaint'
    send(p,{'type':'stream','fps':0});frames(.3);assert frames(.3)==0,'stream did not pause'
    send(p,{'type':'stream','fps':30});send(p,{'type':'data','data':{'color':'#6688ff'}});assert frames(.5)>=1,'stream did not resume'
    print('PASS: 1080p streaming, identical-frame suppression, OC repaint, pause and resume')
finally:close(p)
