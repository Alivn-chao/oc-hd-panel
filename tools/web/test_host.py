import json,os,queue,struct,subprocess,threading,time,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('OCHD_TEST_JAVA','C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot/bin/java.exe'))
cmd=[str(JAVA),'--module-path',str(ROOT/'web-runtime/lib'),'--add-modules','javafx.web,javafx.swing','-Dprism.order=sw','-Dochd.selftest=true','-cp',str(ROOT/'build/web-host.jar')+os.pathsep+str(ROOT/'web-runtime/lib/gson.jar'),'dev.bec.hdweb.Host']
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
   data=read_exact(n);events.put((typ,json.loads(data)if typ==2 else data))
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
try:
 wait('started')
 pack={'id':'test','entry':'index.html','files':{'index.html':"<!doctype html><html><head><link rel='stylesheet' href='style.css'></head><body><h1>OCHD HTML prototype</h1><p id='value'></p><input id='input'><button id='button' onclick='window.clicked=true'>Click test</button><svg width='300' height='80'><rect width='300' height='80' fill='#6688ff'/></svg><canvas id='chart' width='300' height='100'></canvas><script src='main.js'></script></body></html>",'style.css':'body{font:28px sans-serif;background:#eef1f8;color:#203252;padding:40px}input{font:24px sans-serif}','main.js':"OC.onData(function(d){document.getElementById('value').textContent=d.energy;});var ctx=document.getElementById('chart').getContext('2d');ctx.fillStyle='#22bdb8';ctx.fillRect(0,0,250,70);"},'events':['test']}
 if '--render-demo' in sys.argv:
  pack=json.loads((ROOT/'web-demo/app.json').read_text(encoding='utf-8'));pack['files']={name:(ROOT/'web-demo'/name).read_text(encoding='utf-8')for name in pack['files']}
  send({'type':'load','package':pack});wait('ready')
  send({'type':'data','data':{'_online':True,'uptime':1836,'freeMemory':318720,'totalMemory':524288,'target':144000,'counter':17}})
  assert evaluate("document.getElementById('uptime').textContent").endswith('秒')
  for i in range(18):
   send({'type':'data','data':{'_online':True,'uptime':1836+i,'freeMemory':318720+(i%7)*8000,'totalMemory':524288,'target':144000,'counter':17+i}})
  time.sleep(.4)
  rect=json.loads(evaluate("JSON.stringify((function(){var r=document.getElementById('save').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})())"))
  send(dict(type='input',kind='down',**rect));send(dict(type='input',kind='up',**rect));event=wait('event')
  assert event['event']=='set_target'and event['args']['target']==144000
  send({'type':'ack','data':{'request':event['request'],'ok':True,'message':'目标已保存（离屏接口测试）'}});time.sleep(.1)
  assert '离屏接口测试' in evaluate("document.getElementById('notice').textContent")
  send({'type':'snapshot'});png=wait('frame');(ROOT/'build/web-demo-preview.png').write_bytes(png)
  print('Rendered real HTML demo');raise SystemExit(0)
 send({'type':'load','package':pack});wait('ready')
 assert evaluate('typeof OC')=='object'
 send({'type':'data','data':{'energy':123456}})
 assert evaluate("document.getElementById('value').textContent")=='123456'
 assert '238, 241, 248' in evaluate('getComputedStyle(document.body).backgroundColor')
 evaluate("OC.emit('test',{value:42}).then(function(a){window.lastAck=a.message;})")
 event=wait('event');assert event['event']=='test'and event['args']['value']==42
 send({'type':'ack','data':{'request':event['request'],'ok':True,'message':'saved'}})
 time.sleep(.1)
 assert evaluate('window.lastAck')=='saved'
 evaluate("document.getElementById('input').focus()")
 send({'type':'input','kind':'paste','text':'paste-01234567'})
 assert evaluate("document.getElementById('input').value")=='paste-01234567'
 send({'type':'input','kind':'key','key':'DIGIT7','character':'7'})
 assert evaluate("document.getElementById('input').value").endswith('7')
 rect=json.loads(evaluate("JSON.stringify((function(){var r=document.getElementById('button').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})())"))
 send(dict(type='input',kind='down',**rect));send(dict(type='input',kind='up',**rect))
 assert evaluate('window.clicked')=='true'
 # CSS coordinates shrink with WebView zoom; GUI input stays in physical viewport coordinates.
 before=float(evaluate('window.innerWidth'))
 send({'type':'zoom','value':1.5});time.sleep(.15)
 assert float(evaluate('window.innerWidth')) < before
 evaluate('window.clicked=false')
 rect=json.loads(evaluate("JSON.stringify((function(){var r=document.getElementById('button').getBoundingClientRect();return {x:(r.x+r.width/2)*1.5,y:(r.y+r.height/2)*1.5};})())"))
 send(dict(type='input',kind='down',**rect));send(dict(type='input',kind='up',**rect))
 assert evaluate('window.clicked')=='true','zoomed mouse coordinates must match button'
 send({'type':'zoom','value':1});time.sleep(.1)
 assert float(evaluate('window.innerWidth'))==before
 send({'type':'snapshot'});png=wait('frame');assert png[:8]==b'\x89PNG\r\n\x1a\n'
 assert struct.unpack('>II',png[16:24])==(1920,1080)
 (ROOT/'build/web-host-preview.png').write_bytes(png)
 print('PASS: actual WebKit HTML/CSS/JS, OC data/event/ack, paste, zoom/reset with matching mouse clicks, SVG/canvas and 1080p PNG')
finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
 err=process.stderr.read().decode(errors='replace')
 if process.returncode not in (0,None):print(err[-3000:])
