"""Actual browser input protocol regression; no Minecraft hardware claim."""
from host_harness import *

try:
 wait('started')
 pack={'id':'editing-test','entry':'index.html','files':{'index.html':'''<!doctype html><html><body><input id="edit" value="abcd"><script>window.keys=[];document.addEventListener('keydown',function(e){keys.push([e.key,e.code,e.keyCode]);});</script></body></html>'''},'events':[]}
 send({'type':'load','package':pack});wait('ready',60)
 evaluate("document.getElementById('edit').focus();document.getElementById('edit').setSelectionRange(4,4)")
 send({'type':'input','kind':'key','key':'BACK_SPACE','character':''})
 print('After backspace:',evaluate("document.getElementById('edit').value"),'keys:',evaluate('JSON.stringify(keys)'))
 assert evaluate("document.getElementById('edit').value")=='abc','Backspace must remove the previous character'
 evaluate("document.getElementById('edit').setSelectionRange(1,1)")
 send({'type':'input','kind':'key','key':'DELETE','character':''})
 print('After delete:',evaluate("document.getElementById('edit').value"),'keys:',evaluate('JSON.stringify(keys)'))
 assert evaluate("document.getElementById('edit').value")=='ac','Delete must remove the next character'
 send({'type':'input','kind':'key','key':'A','character':'','ctrl':True})
 send({'type':'input','kind':'key','key':'BACK_SPACE','character':''})
 assert evaluate("document.getElementById('edit').value")==''
 evaluate("var area=document.createElement('div');area.id='scroll';area.style='position:fixed;left:200px;top:100px;width:300px;height:250px;overflow:auto';area.innerHTML='<div style=\"height:2000px\">scroll test</div>';document.body.appendChild(area)")
 time.sleep(.2) # allow the newly-created scroll layer to reach CEF hit testing
 send({'type':'input','kind':'wheel','x':300,'y':200,'delta':-80})
 time.sleep(.35) # Chromium animates ordinary DOM scrolling
 distance=float(evaluate("document.getElementById('scroll').scrollTop"));assert 80<=distance<=200,str(distance)
 send({'type':'input','kind':'wheel','x':300,'y':200,'delta':80});time.sleep(.35)
 assert float(evaluate("document.getElementById('scroll').scrollTop"))<distance
 print('Native page scroll direction and distance:',distance,'CSS pixels per notch: PASS')
 print('Native input deletion: PASS')
finally:
 try:send({'type':'close'});process.wait(timeout=5)
 except Exception:process.kill()
