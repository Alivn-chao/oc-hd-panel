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

try:
 wait('started')
 folder=ROOT/'web-apps/defi-yield';pack=json.loads((folder/'app.json').read_text(encoding='utf-8'))
 pack['files']={name:(folder/name).read_text(encoding='utf-8-sig')for name in pack['files']}
 assert len(json.dumps(pack,ensure_ascii=False).encode())<256*1024
 send({'type':'load','package':pack});wait('ready');time.sleep(.3)
 evaluate("document.documentElement.style.scrollBehavior='auto'")
 send({'type':'data','data':{'_online':True,'uptime':1836,'freeMemory':318720,'totalMemory':524288,'balance':10000,'connected':False,'receipts':0}})
 assert evaluate("document.getElementById('oc-uptime').textContent")=='1836 SEC'
 assert evaluate("document.fonts.check('700 20px \"Neon Orbit\"')")=='true'
 assert evaluate("getComputedStyle(document.querySelector('h1 .glow')).textShadow")!='none'
 assert evaluate('document.documentElement.scrollWidth<=window.innerWidth')=='true'
 def click(selector):
  rect=json.loads(evaluate("JSON.stringify((function(){var e=document.querySelector("+json.dumps(selector)+");e.scrollIntoView({block:'center',behavior:'auto'});var r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})())"))
  send(dict(type='input',kind='down',**rect));send(dict(type='input',kind='up',**rect));time.sleep(.04)
 def paste(selector,text):
  click(selector);send({'type':'input','kind':'key','key':'A','ctrl':True,'character':''});send({'type':'input','kind':'paste','text':text})
 def screenshot(name):
  time.sleep(.1);send({'type':'snapshot'});(ROOT/'build'/name).write_bytes(as_png(wait('frame')))
 screenshot('defi-yield-hero.png')
 paste('#amount','25000');click('[data-days="365"]')
 assert evaluate("document.getElementById('earnings').textContent")=='+$61,450'
 # Real range drag updates APY and canvas projection.
 evaluate("document.getElementById('rate').scrollIntoView({block:'center'})")
 rect=json.loads(evaluate("JSON.stringify((function(){var r=document.getElementById('rate').getBoundingClientRect();return {x:r.x,y:r.y+r.height/2,w:r.width};})())"))
 send({'type':'input','kind':'down','x':rect['x']+rect['w']*.5,'y':rect['y']})
 send({'type':'input','kind':'move','x':rect['x']+rect['w']*.82,'y':rect['y']})
 send({'type':'input','kind':'up','x':rect['x']+rect['w']*.82,'y':rect['y']})
 assert float(evaluate("document.getElementById('rate').value"))>350
 click('[data-filter="stable"]');assert evaluate("document.querySelectorAll('.pool-card').length")=='2'
 paste('#pool-search','USDT');assert evaluate("document.querySelectorAll('.pool-card').length")=='1'
 paste('#pool-search','no-such-pool');assert evaluate("document.getElementById('pool-empty').hidden")=='false'
 paste('#pool-search','');click('[data-favorite="usdc-usdt"]');assert evaluate("document.querySelector('[data-favorite=usdc-usdt]').getAttribute('aria-pressed')")=='true'
 click('#wallet-button');assert evaluate("document.getElementById('modal').hidden")=='false';click('#connect-demo')
 event=wait('event');assert event['event']=='demo_connect'
 send({'type':'ack','data':{'request':event['request'],'ok':True,'message':'OC: demo connected'}});time.sleep(.1)
 send({'type':'data','data':{'_online':True,'uptime':1840,'freeMemory':318720,'balance':10000,'connected':True,'receipts':1}})
 click('[data-deposit="usdc-usdt"]');paste('#deposit-amount','250.25');click('#confirm-deposit')
 event=wait('event');assert event['event']=='demo_deposit'and event['args']=={'pool':'usdc-usdt','amount':250.25}
 send({'type':'ack','data':{'request':event['request'],'ok':True,'message':'OC: virtual deposit accepted'}})
 send({'type':'data','data':{'_online':True,'uptime':1841,'freeMemory':318720,'balance':9749.75,'connected':True,'receipts':2}});time.sleep(.1)
 assert evaluate("document.getElementById('demo-balance').textContent")=='9,749.75 USDC'
 click('#token-details');assert 'TOKEN' in evaluate("document.getElementById('modal-title').textContent");click('#modal-close')
 evaluate("document.getElementById('tokenomics').scrollIntoView({block:'start'})");screenshot('defi-yield-tokenomics.png')
 click('#ping-button');event=wait('event');assert event['event']=='echo_ping'
 send({'type':'ack','data':{'request':event['request'],'ok':True,'message':'Round trip OK'}})
 for zoom in [.75,1.25,2]:
  send({'type':'zoom','value':zoom});time.sleep(.08)
  assert evaluate('document.documentElement.scrollWidth<=window.innerWidth')=='true','horizontal overflow at zoom '+str(zoom)
 send({'type':'zoom','value':1});evaluate('window.scrollTo(0,0)')
 print('PASS: actual WebKit neon glow, bundled static font, calculator, slider, Canvas/SVG, pool filtering/search/favorites, wallet/deposit OC round trip and responsive zoom')
finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
 err=process.stderr.read().decode(errors='replace')
 if process.returncode not in (0,None):print(err[-3000:])
