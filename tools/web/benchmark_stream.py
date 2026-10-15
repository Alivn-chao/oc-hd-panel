"""Compare sustained 1080p delivery against alpha5's render-thread polling (no Minecraft world)."""
import json, os, queue, struct, subprocess, threading, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=os.environ.get('OCHD_TEST_JAVA','java')
LIB=ROOT/'web-runtime/lib'
PAGE="""<html><body style='margin:0;background:#111722'><canvas id='c' width='1280' height='720'></canvas><script>var c=document.getElementById('c').getContext('2d'),n=0;function draw(){n++;c.fillStyle='hsl('+n%360+',55%,35%)';c.fillRect(0,0,1280,720);c.fillStyle='#22bdb8';c.fillRect((n*7)%1100,80,180,500);requestAnimationFrame(draw);}draw();</script></body></html>"""

def launch(jar):
    p=subprocess.Popen([JAVA,'--module-path',str(LIB),'--add-modules','javafx.web,javafx.swing','-Dprism.order='+os.environ.get('OCHD_TEST_PRISM','d3d,sw'),'-cp',str(jar)+os.pathsep+str(LIB/'gson.jar'),'dev.bec.hdweb.Host'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    q=queue.Queue()
    def exact(n):
        data=bytearray()
        while len(data)<n:
            chunk=p.stdout.read(n-len(data))
            if not chunk:raise EOFError('runtime closed')
            data.extend(chunk)
        return bytes(data)
    def read():
        try:
            while True:
                typ,n=struct.unpack('>BI',exact(5));data=exact(n);q.put((time.perf_counter(),typ,json.loads(data) if typ==2 else n))
        except Exception as e:q.put((time.perf_counter(),-1,str(e)))
    threading.Thread(target=read,daemon=True).start()
    return p,q

def send(p,c):p.stdin.write(json.dumps(c).encode()+b'\n');p.stdin.flush()
def event(q,kind):
    end=time.perf_counter()+20
    while time.perf_counter()<end:
        _,typ,obj=q.get(timeout=20)
        if typ==-1:raise RuntimeError(obj)
        if typ==2 and obj.get('type')=='error':raise RuntimeError(obj)
        if typ==2 and obj.get('type')==kind:return
    raise TimeoutError(kind)
def close(p):
    try:send(p,{'type':'close'});p.wait(timeout=5)
    except Exception:p.kill();p.wait()

def measure(jar,stream):
    p,q=launch(jar)
    try:
        event(q,'started');send(p,{'type':'load','package':{'entry':'index.html','files':{'index.html':PAGE}}});event(q,'ready')
        if stream:send(p,{'type':'stream','fps':60})
        start=time.perf_counter();pending=False;frames=[];total_bytes=0;next_tick=start
        while time.perf_counter()-start<6:
            now=time.perf_counter()
            if not stream and now>=next_tick:
                next_tick=now+1/60
                if not pending:send(p,{'type':'snapshot'});pending=True
            try:t,typ,obj=q.get(timeout=.001)
            except queue.Empty:continue
            if typ in (1,3) and t-start>=2:frames.append(t);total_bytes+=obj
            if typ==2 and obj.get('type')=='snapshot_done':pending=False
            if typ==-1 or (typ==2 and obj.get('type')=='error'):raise RuntimeError(obj)
        result={'fps':round(len(frames)/4,2),'frames':len(frames),'MiB_per_second':round(total_bytes/4/1048576,2)}
        if stream:
            send(p,{'type':'stream','fps':0});time.sleep(.3)
            while not q.empty():q.get_nowait()
            time.sleep(.3);assert not any(q.get_nowait()[1] in (1,3) for _ in range(q.qsize())),'stream did not stop'
        return result
    finally:close(p)

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',type=Path);args=parser.parse_args()
    results={}
    if args.baseline:results['alpha5_polling']=measure(args.baseline,False)
    results['alpha6_stream']=measure(ROOT/'build/web-host.jar',True)
    print(json.dumps(results,indent=2));(ROOT/'build/stream-benchmark.json').write_text(json.dumps(results,indent=2))
