"""Actual CEF input against the bundled BEC page, without machine control."""
from host_harness import *

def key(name,**extra):send(dict(type='input',kind='key',key=name,character='',**extra))
def click(selector):
 rect=json.loads(evaluate("JSON.stringify((function(){var e=document.querySelector("+json.dumps(selector)+");e.scrollIntoView({block:'center'});var r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})())"))
 send(dict(type='input',kind='down',**rect));send(dict(type='input',kind='up',**rect));evaluate('document.activeElement.tagName')

try:
 wait('started');page=Path(sys.argv[sys.argv.index('--page')+1]).read_text(encoding='utf-8')
 send({'type':'load','package':{'id':'bec-editing-test','entry':'index.html','files':{'index.html':page},'events':['save_field']}});wait('ready',60)
 send({'type':'data','data':{'revision':'1','values':{'n1.address':'','n1.redstone':'','n1.group':'1','n1.pauseSide':'1'},'group':1,'groupCount':2,'mode':'binding','configured':True}})
 evaluate("BEC_APP.select('node-1')");click('#field-address')
 send({'type':'input','kind':'paste','text':'abcdef'});assert evaluate("document.getElementById('field-address').value")=='abcdef'
 key('BACK_SPACE');assert evaluate("document.getElementById('field-address').value")=='abcde'
 key('HOME');key('RIGHT');key('DELETE')
 assert evaluate("document.getElementById('field-address').value")=='acde'
 assert evaluate("getComputedStyle(document.getElementById('field-address')).caretColor")=='rgb(35, 110, 232)'
 assert evaluate('document.hasFocus()')=='true','focused browser must render insertion caret'
 click('[data-clear="address"]');assert evaluate("document.getElementById('field-address').value")==''
 click('#field-group + .ochd-select')
 assert evaluate("document.querySelector('.ochd-select-menu')!==null")=='true'
 key('ArrowDown' if not CEF else 'DOWN');key('ENTER')
 assert evaluate("document.getElementById('field-group').value")=='2'
 assert evaluate("document.querySelector('.ochd-select-menu')===null")=='true'
 click('#field-group + .ochd-select');click('.ochd-select-option:first-child')
 assert evaluate("document.getElementById('field-group').value")=='1'
 print('BEC native deletion, focus, clear button and visible dropdown mouse/keyboard selection: PASS')
 rect=json.loads(evaluate("JSON.stringify((function(){var c=BEC_APP.scene.canvas,r=c.getBoundingClientRect();for(var i=1;i<6;i++)for(var j=1;j<6;j++){var x=r.left+r.width*i/6,y=r.top+r.height*j/6;if(document.elementFromPoint(x,y)===c)return{x:x,y:y};}throw Error('No exposed canvas');})())"))
 evaluate("window.wheelInfo=null;BEC_APP.scene.canvas.addEventListener('wheel',function(e){wheelInfo={delta:e.deltaY,mode:e.deltaMode};})")
 before=float(evaluate('BEC_APP.scene.camera.distance'));send(dict(type='input',kind='wheel',delta=80,**rect))
 after=float(evaluate('BEC_APP.scene.camera.distance'));print('Wheel',evaluate('JSON.stringify(wheelInfo)'),before,'->',after)
 assert after/before<.9,'one physical notch must zoom by at least 10 percent'
 print('BEC wheel sensitivity: PASS')
finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
