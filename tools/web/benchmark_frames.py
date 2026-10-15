import json,os,queue,struct,subprocess,threading,time,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('OCHD_TEST_JAVA','C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot/bin/java.exe'))
cmd=[str(JAVA),'--module-path',str(ROOT/'web-runtime/lib'),'--add-modules','javafx.web,javafx.swing','-Dprism.order='+os.environ.get('OCHD_TEST_PRISM','sw'),'-Dochd.selftest=true','-cp',str(ROOT/'build/web-host.jar')+os.pathsep+str(ROOT/'web-runtime/lib/gson.jar'),'dev.bec.hdweb.Host']
process=subprocess.Popen(cmd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0));events=queue.Queue()
def read_exact(n):
 data=b''
 while len(data)<n:
  part=process.stdout.read(n-len(data))
  if not part:raise EOFError('host closed')
  data+=part
 return data
def reader():
 try:
  while True:
   typ=read_exact(1)[0];n=struct.unpack('>I',read_exact(4))[0];assert n<12000000
   data=read_exact(n);typ=1 if typ==3 else typ;events.put((typ,json.loads(data)if typ==2 else data))
 except Exception as e:events.put(('error',str(e)))
threading.Thread(target=reader,daemon=True).start()
def send(value):process.stdin.write(json.dumps(value,ensure_ascii=False).encode()+b'\n');process.stdin.flush()
deferred=[]
def wait(wanted,timeout=20):
 for i,(typ,obj) in enumerate(deferred):
  if (wanted=='frame'and typ==1)or(typ==2 and obj.get('type')==wanted):deferred.pop(i);return obj
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  typ,obj=events.get(timeout=max(.1,deadline-time.monotonic()))
  if typ=='error':raise RuntimeError(obj)
  if typ==2 and obj.get('type')=='error':raise RuntimeError(obj)
  if (wanted=='frame'and typ==1)or(typ==2 and obj.get('type')==wanted):return obj
  deferred.append((typ,obj))
 raise TimeoutError(wanted)
def evaluate(script):send({'type':'eval','script':script});return wait('eval')['message']
def as_png(data):
 if data[:8]==b'\x89PNG\r\n\x1a\n':return data
 import zlib,io
 from PIL import Image
 w,h=struct.unpack('>II',data[:8]);raw=zlib.decompress(data[8:]);assert len(raw)==w*h*4
 output=io.BytesIO();Image.frombytes('RGBA',(w,h),raw,'raw','ARGB').save(output,format='PNG');return output.getvalue()

import statistics
try:
 wait('started');send({'type':'load','package':{'entry':'index.html','files':{'index.html':"<html><body style='font:24px sans-serif;background:#eef1f8'><input id='slider' type='range' style='width:800px'><div style='height:4000px;background:linear-gradient(#88aaff,#22bbaa)'>Scroll performance</div></body></html>"}}});wait('ready')
 times=[];sizes=[]
 for i in range(26):
  send({'type':'input','kind':'wheel','x':600,'y':500,'delta':-80})
  start=time.perf_counter();send({'type':'snapshot'});frame=wait('frame');elapsed=(time.perf_counter()-start)*1000
  if i>=6:times.append(elapsed);sizes.append(len(frame))
 print(json.dumps({'median_ms':round(statistics.median(times),2),'p95_ms':round(sorted(times)[int(len(times)*.95)-1],2),'mean_frame_bytes':round(statistics.mean(sizes))}))
finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
