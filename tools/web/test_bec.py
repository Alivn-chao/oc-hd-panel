from host_harness import *

try:
 wait('started')
 page=Path(sys.argv[sys.argv.index('--page')+1]).read_text(encoding='utf-8')
 pack={'id':'bec-test','entry':'index.html','files':{'index.html':page},'events':[]}
 send({'type':'load','package':pack});wait('ready',60);time.sleep(1)
 print('Renderer:',evaluate('BEC_APP.scene.mode'))
 print('GPU:',evaluate("(function(){var g=BEC_APP.scene.gl;if(!g)return 'none';var e=g.getExtension('WEBGL_debug_renderer_info');return e?g.getParameter(e.UNMASKED_RENDERER_WEBGL):g.getParameter(g.RENDERER);})()"))
 if CEF:assert evaluate('BEC_APP.scene.mode')=='WebGL'
 print('Viewport:',evaluate('JSON.stringify({width:innerWidth,dpr:devicePixelRatio,zoom:1})'))
 script="""JSON.stringify((function(){var s=BEC_APP.scene,times=[];for(var i=0;i<6;i++){s.camera.yaw+=.025;var t=performance.now();s.draw();times.push(performance.now()-t);}return {ms:times,faces:Object.keys(s.meshes).reduce(function(n,k){return n+s.meshes[k].faces.length;},0),draws:s.drawCount};})())"""
 print('Rotation benchmark:',evaluate(script))
 send({'type':'snapshot'});png=as_png(wait('frame',60));(ROOT/'build/bec-native-preview.png').write_bytes(png)
 rect=json.loads(evaluate("JSON.stringify((function(){var r=BEC_APP.scene.canvas.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2};})())"))
 for button in [1,2]:
  rect=json.loads(evaluate("JSON.stringify((function(){var c=BEC_APP.scene.canvas,r=c.getBoundingClientRect();for(var i=1;i<5;i++)for(var j=1;j<5;j++){var x=r.left+r.width*i/6,y=r.top+r.height*j/6;if(document.elementFromPoint(x,y)===c)return{x:x,y:y};}throw Error('No exposed canvas');})())"))
  before=evaluate('JSON.stringify(BEC_APP.scene.camera.target)')
  send(dict(type='input',kind='down',button=button,**rect))
  send(dict(type='input',kind='move',button=button,x=rect['x']+70,y=rect['y']+25));time.sleep(.2)
  send(dict(type='input',kind='up',button=button,x=rect['x']+70,y=rect['y']+25))
  after=evaluate('JSON.stringify(BEC_APP.scene.camera.target)')
  print('Pan button',button,':',before,'->',after)
  if '--assert-pan' in sys.argv:assert before!=after,'right/middle drag must pan BEC camera'
 if CEF:
  assert evaluate('devicePixelRatio')=='1.5'
  assert 'SwiftShader' not in evaluate("(function(){var g=BEC_APP.scene.gl,e=g.getExtension('WEBGL_debug_renderer_info');return g.getParameter(e.UNMASKED_RENDERER_WEBGL);})()"),'Expected real GPU WebGL'

finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
 err=process.stderr.read().decode(errors='replace')
 if process.returncode not in (0,None):print(err[-3000:])
