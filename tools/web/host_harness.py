import json,os,queue,struct,subprocess,threading,time,sys,mmap,tempfile,atexit
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('OCHD_TEST_JAVA','C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot/bin/java.exe'))
cmd=[str(JAVA),'--module-path',str(ROOT/'web-runtime/lib'),'--add-modules','javafx.web,javafx.swing','-Dprism.order='+os.environ.get('OCHD_TEST_PRISM','sw'),'-Dochd.selftest=true','-cp',str(ROOT/'build/web-host.jar')+os.pathsep+str(ROOT/'web-runtime/lib/gson.jar'),'dev.bec.hdweb.Host']
CEF=os.environ.get('OCHD_TEST_CEF')
if CEF:
 runtime=Path(CEF).resolve()
 cmd=[str(JAVA),'-Dochd.selftest=true','-Dochd.web.root='+str(runtime),'-Djava.library.path='+str(runtime/'native'),'-cp',str(runtime/'cef-host.jar')+os.pathsep+str(runtime/'cef-lib/*'),'dev.bec.hdweb.CefHost']
mapping=None
if CEF and os.environ.get('OCHD_TEST_MAPPING')=='1':
 # Named files are required for mapping across processes on Windows.
 with tempfile.NamedTemporaryFile(dir=ROOT/'build',prefix='test-cef-frames-',suffix='.bin',delete=False) as f:
  mapname=Path(f.name);f.truncate(1920*1080*4*2)
 mapped_source=open(mapname,'r+b');mapping=mmap.mmap(mapped_source.fileno(),0)
 cmd.insert(1,'-Dochd.web.frames='+str(mapname))
 def cleanup_mapping():
  mapping.close();mapped_source.close();mapname.unlink(missing_ok=True)
 atexit.register(cleanup_mapping)
send_lock=threading.Lock();ack_queue=queue.Queue()
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
   data=read_exact(n)
   if typ==4:
    slot=data[0];mapping.seek(slot*1920*1080*4);bgra=mapping.read(1920*1080*4)
    from PIL import Image
    import io
    output=io.BytesIO();Image.frombytes('RGBA',(1920,1080),bgra,'raw','BGRA').save(output,format='PNG')
    data=output.getvalue();typ=1
    ack_queue.put({'type':'frame_ack','slot':slot})
   typ=1 if typ==3 else typ;events.put((typ,json.loads(data)if typ==2 else data))
 except Exception as e:events.put(('error',str(e)))
def send(value):
 with send_lock:process.stdin.write(json.dumps(value,ensure_ascii=False).encode()+b'\n');process.stdin.flush()
def acknowledge_loop():
 try:
  while True:send(ack_queue.get())
 except (OSError,ValueError):pass
threading.Thread(target=acknowledge_loop,daemon=True).start()
threading.Thread(target=reader,daemon=True).start()
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
def evaluate(script):
 if CEF:time.sleep(.03) # CEF posts native input and JS to separate renderer queues.
 send({'type':'eval','script':script});return wait('eval')['message']
def as_png(data):
 if data[:8]==b'\x89PNG\r\n\x1a\n':return data
 import zlib,io
 from PIL import Image
 w,h=struct.unpack('>II',data[:8]);raw=zlib.decompress(data[8:]);assert len(raw)==w*h*4
 output=io.BytesIO();Image.frombytes('RGBA',(w,h),raw,'raw','ARGB').save(output,format='PNG');return output.getvalue()

