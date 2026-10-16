import importlib.util,unittest,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('webtests',ROOT/'tools/web/test_lua.py');web=importlib.util.module_from_spec(spec);spec.loader.exec_module(web)

class DefiPageTests(unittest.TestCase):
 def test_real_page_package_is_self_contained_and_bounded(self):
  lua=web.fixture();files=lua.table();folder=ROOT/'web-apps/defi-yield'
  for p in folder.iterdir():files['/apps/defi-yield/'+p.name]=p.read_text(encoding='utf-8-sig')
  lua.globals().files=files
  lua.execute("io.open=function(p,m)assert(m=='r');if not files[p]then return end;return {read=function(_,n)return files[p]:sub(1,n)end,close=function()end}end;pack=W.package('/apps/defi-yield')")
  self.assertEqual(lua.eval('pack.id'),'neon-yield');self.assertIn('data:font/ttf;base64,',lua.eval("pack.files['style.css']"))
  manifest=json.loads((folder/'app.json').read_text());manifest['files']={n:(folder/n).read_text(encoding='utf-8-sig')for n in manifest['files']}
  self.assertLess(len(json.dumps(manifest,ensure_ascii=False).encode()),256*1024)
 def setup_runner(self):
  lua=web.fixture();lua.execute('''
  now=0;pulls=0;published={};acks={};released=0
  W.package=function(p)assert(p=='/home/ochd_apps/defi-yield');return {id='neon-yield'}end
  package.preload.computer=function()return {uptime=function()return now end,freeMemory=function()return 1024 end,totalMemory=function()return 4096 end}end
  local panel={setWebApp=function(p)return true end,publish=function(d)published[#published+1]=d;return true end,
   ack=function(id,ok,message)acks[#acks+1]={ok=ok,message=message}end,release=function()released=released+1 end}
  for name,fn in pairs(panel)do panel[name]=setmetatable({fn=fn},{__call=function(self,...)return self.fn(...)end})end
  package.preload.component=function()return {methods=function()return {setWebApp=false,publish=false}end,list=function()local once=true;return function()if once then once=false;return 'panel'end end end,proxy=function()return panel end}end
  local cases={{'demo_connect','{}'},{'demo_deposit','{"pool":"usdc-usdt","amount":250.25}'},{'demo_deposit','{"pool":"usdc-usdt","amount":999999}'},{'demo_deposit','{"pool":"unknown","amount":1}'},{'demo_disconnect','{}'},{'demo_deposit','{"pool":"usdc-usdt","amount":1}'},{'echo_ping','{}'}}
  package.preload.event=function()return {pull=function()now=now+1;pulls=pulls+1;if cases[pulls]then return 'hd_panel_web','panel',cases[pulls][1],'player',pulls,cases[pulls][2]end;return 'key_down','keyboard',113,16 end}end
  loadfile=function(p)assert(p=='/home/ochd_web.lua');return function()return W end end;print=function()end
  ''');return lua
 def test_callable_oc_callbacks_validate_virtual_deposits_and_release(self):
  lua=self.setup_runner();lua.execute((ROOT/'oc/defi_yield.lua').read_text(encoding='utf-8-sig'))
  self.assertEqual([lua.eval('acks[%d].ok'%i)for i in range(1,8)],[True,True,False,False,True,False,True])
  self.assertEqual(lua.eval('published[#published].balance'),9749.75);self.assertEqual(lua.eval('published[#published].receipts'),4);self.assertEqual(lua.eval('released'),1)
 def test_installation_is_idempotent_and_preserves_old_demo_and_v2(self):
  lua=web.DemoInstallerTests().setup_install();lua.execute("files['/home/ochd_web_demo.lua']='original-demo'")
  source=(ROOT/'oc/install_defi_yield.lua').read_text(encoding='utf-8');lua.execute(source);first=dict(lua.globals().files);lua.execute(source)
  self.assertEqual(first,dict(lua.globals().files));self.assertEqual(lua.globals().files['/home/ochd_web_demo.lua'],'original-demo');self.assertEqual(lua.globals().files['/home/bec_v2/config.dat'],lua.globals().original)
  self.assertIn('DEMO BALANCE',lua.globals().files['/home/ochd_apps/defi-yield/index.html'])
 def test_install_failure_restores_existing_runner(self):
  lua=web.DemoInstallerTests().setup_install();lua.execute("""files['/home/ochd_web.lua']='old-helper';files['/home/ochd_defi_yield.lua']='old-runner';local fs=require('filesystem');local rename=fs.rename;fs.rename=function(a,b)if a:find('ochd_defi_yield.lua.web-new-',1,true)then return false end;return rename(a,b)end""")
  with self.assertRaisesRegex(Exception,'originals restored'):lua.execute((ROOT/'oc/install_defi_yield.lua').read_text(encoding='utf-8'))
  self.assertEqual(lua.globals().files['/home/ochd_defi_yield.lua'],'old-runner');self.assertEqual(lua.globals().files['/home/ochd_web.lua'],'old-helper')
if __name__=='__main__':unittest.main()
