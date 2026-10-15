import json
import unittest
import importlib.util
from pathlib import Path
from lupa.lua52 import LuaRuntime

ROOT=Path(__file__).resolve().parents[2]
SOURCE=(ROOT/'oc/ochd_web.lua').read_text(encoding='utf-8')
def fixture():
    lua=LuaRuntime(unpack_returned_tuples=True)
    lua.globals().uchar=chr
    lua.execute("package.preload.unicode=function()return {char=uchar}end;package.preload.filesystem=function()return {concat=function(a,b)return a..'/'..b end}end")
    lua.globals().W=lua.eval('function()'+SOURCE+'\nend')()
    return lua

class WebLuaTests(unittest.TestCase):
    def test_json_unicode_surrogates_false_and_array(self):
        lua=fixture();lua.execute('v=W.decode(...)','{"name":"\\u4f60\\u597d \\ud83d\\ude00","enabled":false,"rows":[12,24],"target":1.5e3}')
        self.assertEqual(lua.eval('v.name'),'你好 😀');self.assertFalse(lua.eval('v.enabled'));self.assertEqual(lua.eval('v.rows[2]'),24);self.assertEqual(lua.eval('v.target'),1500)
    def test_rejects_duplicate_deep_invalid_and_executable_content(self):
        for raw in ['{"x":1,"x":2}','[[[[[[[[[0]]]]]]]]]','{"x":01}','{"x":1.}','{"x":1.e2}','os.execute("bad")','{"x":"\\ud800"}','{} trailing']:
            with self.subTest(raw=raw):
                with self.assertRaises(Exception):fixture().execute('W.decode(...)',raw)
    def test_actual_demo_package_loads_only_manifest_resources(self):
        lua=fixture();files=lua.table()
        for p in (ROOT/'web-demo').iterdir():files['/apps/demo/'+p.name]=p.read_text(encoding='utf-8')
        lua.globals().files=files
        lua.execute("io.open=function(p,m)assert(m=='r');if not files[p]then return end;return {read=function(_,n)return files[p]:sub(1,n)end,close=function()end}end;pack=W.package('/apps/demo')")
        self.assertEqual(lua.eval('pack.id'),'ochd-demo');self.assertEqual(lua.eval('pack.events[1]'),'set_target');self.assertIn('OC.onData',lua.eval("pack.files['main.js']"))
        lua.globals().files['/apps/demo/app.json']='{"files":["../secret"]}'
        with self.assertRaisesRegex(Exception,'Invalid page path'):lua.execute("W.package('/apps/demo')")
    def test_demo_round_trip_updates_only_demo_state_and_releases(self):
        lua=fixture();lua.globals().pack=lua.table_from({'id':'test'})
        lua.execute("""
        now=0;pulls=0;published={};released=0
        W.package=function()return pack end
        package.preload.computer=function()return {uptime=function()return now end,freeMemory=function()return 1024 end,totalMemory=function()return 4096 end}end
        local panel={setWebApp=function(p)assert(p==pack);return true end,publish=function(data)published[#published+1]=data;return true end,
         ack=function(request,ok,message)assert(request==9 and ok);acked=true end,release=function()released=released+1 end}
        package.preload.component=function()return {list=function()local once=true;return function()if once then once=false;return 'panel'end end end,proxy=function()return panel end}end
        package.preload.event=function()return {pull=function(dt)now=now+1;pulls=pulls+1
         if pulls==1 then return 'hd_panel_web','panel','set_target','player',9,'{"target":123}'end
         if pulls==3 then return 'key_down','keyboard',113,16 end
        end}end
        loadfile=function(p)assert(p=='/home/ochd_web.lua');return function()return W end end
        print=function()end
        """)
        lua.execute((ROOT/'oc/web_demo.lua').read_text(encoding='utf-8'))
        self.assertTrue(lua.globals().acked);self.assertEqual(lua.eval('published[2].target'),123);self.assertEqual(lua.globals().released,1)

class DemoInstallerTests(unittest.TestCase):
    def setup_install(self):
        spec=importlib.util.spec_from_file_location('binding_fixtures',ROOT/'tools/test_bindings.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        lua=module.fixture();lua.execute("local fs=require('filesystem');fs.path=function(p)return p:match('^(.*)/')end;fs.makeDirectory=function()return true end")
        return lua
    def test_installer_is_self_contained_idempotent_and_does_not_start_demo(self):
        lua=self.setup_install();source=(ROOT/'oc/install_web_demo.lua').read_text(encoding='utf-8');lua.execute(source)
        contents=dict(lua.globals().files);lua.execute(source);self.assertEqual(contents,dict(lua.globals().files))
        self.assertEqual(lua.globals().files['/home/ochd_web.lua'],SOURCE)
        self.assertIn('setWebApp',lua.globals().files['/home/ochd_web_demo.lua'])
        self.assertEqual(lua.globals().files['/home/bec_v2/config.dat'],lua.globals().original)
    def test_second_file_replace_failure_restores_existing_files(self):
        lua=self.setup_install();lua.execute("""
        files['/home/ochd_web.lua']='old-helper';files['/home/ochd_web_demo.lua']='old-demo'
        local fs=require('filesystem');local rename=fs.rename
        fs.rename=function(a,b)if a:find('ochd_web_demo.lua.web-new-',1,true)then return false end;return rename(a,b)end
        """)
        with self.assertRaisesRegex(Exception,'originals restored'):lua.execute((ROOT/'oc/install_web_demo.lua').read_text(encoding='utf-8'))
        self.assertEqual(lua.globals().files['/home/ochd_web.lua'],'old-helper');self.assertEqual(lua.globals().files['/home/ochd_web_demo.lua'],'old-demo')

if __name__=='__main__':unittest.main()
