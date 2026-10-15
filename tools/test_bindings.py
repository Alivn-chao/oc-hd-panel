import unittest
from pathlib import Path
from lupa.lua52 import LuaRuntime

SOURCE=(Path(__file__).resolve().parents[1]/'oc/ochd_bindings.lua').read_text(encoding='utf-8')
SETUP=r'''
files={};moves=0;writes=0
local function enc(v)
 if type(v)=='table'then local parts={};for k,x in pairs(v)do parts[#parts+1]='['..enc(k)..']='..enc(x)end;return '{'..table.concat(parts,',')..'}'
 elseif type(v)=='string'then return string.format('%q',v)else return tostring(v)end
end
package.preload.serialization=function()return {serialize=function(v,pretty)assert(pretty==false);return enc(v)end,
 unserialize=function(s)return assert(load('return '..s,'data','t',{}))()end}end
package.preload.filesystem=function()return {exists=function(p)return files[p]~=nil end,
 rename=function(a,b)
  if failRename and a:find('.hd.new.',1,true)then return false end
  assert(files[a]and not files[b]);files[b]=files[a];files[a]=nil;return true
 end}end
io.open=function(p,m)
 if m=='r'then if not files[p]then return end;return {read=function()return files[p]end,close=function()end}end
 assert(m=='w');files[p]='';return {write=function(_,s)files[p]=s;return true end,
  flush=function()assert(not failFlush,'disk full');return true end,close=function()end}
end
package.preload.component=function()return {
 methods=function()return {getRequiredCondensate=false,getStoredCondensate=false}end,
 invoke=function(a,m,...)
  assert(m~='transferItem'and m~='setOutput'and m~='setWorkAllowed','Unexpected machine mutation')
  if m=='getState'then return active and 'crafting'or 'idle'end
  if m=='getOutput'then return 15 end
  if m=='isWorkAllowed'or m=='isMachineActive'then return false end
  error('unexpected method '..m)
 end}end
C={path=function(s)return '/home/bec_v2/'..s end,
 read=function(p)if files[p]then return require('serialization').unserialize(files[p])end end,
 validate=function(cfg,live)assert(live and #cfg.nodes>0 and #cfg.groups[1].generators>0);validated=true end}
local function id(n)return string.format('%08x-1234-4567-89ab-123456789abc',n)end
cfg={version=2,mainInterface=id(1),recipeDirectory='/home/bec_v2/recipes',cacheTarget=144000,
 groups={{name='group1',storage=id(2),gate=id(3),subInterface=id(4),transposer=id(5),mainSide=4,subSide=5,
 generators={id(6)},bulkTransposers={id(7)},nanites={enabled=true,mode='cell-io',transposer=id(8),loadRedstone=id(9),returnRedstone=id(10),
 bankSide=0,loadSide=3,returnSide=2,loadOutputSide=0,returnOutputSide=0}}},
 nodes={{name='node3',slot=3,address=id(11),redstone=id(12),pauseSide=0,gate=id(3),group=1}}}
files[C.path('config.dat')]=require('serialization').serialize(cfg,false)
original=files[C.path('config.dat')]
function payload(fields,base)
 local parts={};if base then parts[1]='_revision='..base end
 for _,f in ipairs(fields)do parts[#parts+1]=f.key..'='..f.value end
 return table.concat(parts,'\n')
end
'''

def fixture():
    lua=LuaRuntime(unpack_returned_tuples=True);lua.execute(SETUP)
    lua.globals().B=lua.eval('function()'+SOURCE+'\nend')()
    lua.execute('fields=B.v2Fields(cfg);data=payload(fields,B.revision(original))')
    return lua

class BindingTests(unittest.TestCase):
    def test_v2_roundtrip_preserves_holes_zero_direction_and_cell_io(self):
        lua=fixture();lua.execute('new=B.applyV2(cfg,data,B.revision(original))')
        self.assertEqual(lua.eval('new.nodes[1].slot'),3)
        self.assertEqual(lua.eval('new.nodes[1].pauseSide'),0)
        self.assertEqual(lua.eval('new.groups[1].nanites.loadSide'),3)
        self.assertEqual(lua.eval('new.groups[1].nanites.mode'),'cell-io')

    def test_save_validates_and_backs_up_without_machine_writes(self):
        lua=fixture();lua.execute('new,backup=B.saveV2(C,cfg,data,1,original)')
        self.assertTrue(lua.globals().validated)
        self.assertEqual(lua.globals().files['/home/bec_v2/config.dat.hd.bak.1'],lua.globals().original)

    def test_stale_editor_or_external_file_change_refuses(self):
        lua=fixture()
        with self.assertRaisesRegex(Exception,'配置已变化'):lua.execute("B.saveV2(C,cfg,data:gsub('_revision=%d+','_revision=0'),1,original)")
        lua.execute("files[C.path('config.dat')]=original..' '")
        with self.assertRaisesRegex(Exception,'其他程序修改'):lua.execute('B.saveV2(C,cfg,data,1,original)')

    def test_unfinished_production_pending_or_bee_operation_refuses(self):
        for path,body in [('state/node-1/production.journal',"{stage='watching'}"),
                          ('state/node-1/production.journal',"{stage='stopped',pending={}}"),
                          ('state/group-1/nanites-cell.journal',"{stage='moving-cell'}")]:
            lua=fixture();lua.execute("files[C.path(...)]=require('serialization').serialize("+body+",false)",path)
            with self.assertRaises(Exception):lua.execute('B.saveV2(C,cfg,data,1,original)')
            self.assertEqual(lua.globals().files['/home/bec_v2/config.dat'],lua.globals().original)

    def test_active_old_binding_refuses(self):
        lua=fixture();lua.execute('active=true')
        with self.assertRaisesRegex(Exception,'仍在生产'):lua.execute('B.saveV2(C,cfg,data,1,original)')

    def test_failed_replacement_restores_original(self):
        lua=fixture();lua.execute('failRename=true')
        with self.assertRaisesRegex(Exception,'原配置已恢复'):lua.execute('B.saveV2(C,cfg,data,1,original)')
        self.assertEqual(lua.globals().files['/home/bec_v2/config.dat'],lua.globals().original)

    def test_invalid_and_duplicate_fields_rejected(self):
        lua=fixture()
        for raw in ['node=not-an-address','node=x\nnode=x','unknown=value']:
            with self.assertRaises(Exception):lua.execute('B.parse(...,{{key="node",kind="uuid"}})',raw)

    def test_new_config_preserves_all_sixteen_positions_and_compacts_only_on_save(self):
        lua=fixture();lua.execute("for _,f in ipairs(fields)do if f.key=='n16.address'then f.value=cfg.nodes[1].address end;if f.key=='n16.redstone'then f.value=cfg.nodes[1].redstone end end;new=B.applyV2(cfg,payload(fields))")
        self.assertEqual(lua.eval('#new.nodes'),2)
        self.assertEqual(lua.eval('new.nodes[2].slot'),16)

    def test_setup_page_survives_initial_panel_rate_limit_and_never_starts_production(self):
        lua=fixture()
        lua.execute(r'''
          now=0;updates=0;released=0;pulls=0
          package.preload.computer=function()return {uptime=function()return now end}end
          package.preload.event=function()return {pull=function(dt)
            now=now+dt;pulls=pulls+1;if pulls>10 then return 'key_down','keyboard',113,16 end
          end}end
          local c=require('component');c.list=function()local first=true;return function()if first then first=false;return 'panel'end end end
          c.proxy=function()return {setPage=function()return true end,
            update=function(data)updates=updates+1;assert(data.bindingEditable);if updates==1 then return false,'rate limited'end;return true end,
            release=function()released=released+1 end}end
          loadfile=function(path)
            if path=='/home/bec_v2/lib/config.lua'then return function()return C end end
            assert(path=='/home/ochd_bindings.lua');return function()return B end
          end
        ''')
        setup=(Path(__file__).resolve().parents[1]/'oc/bec_v2_setup_hd.lua').read_text(encoding='utf-8')
        lua.execute(setup)
        self.assertGreaterEqual(lua.globals().updates,2)
        self.assertEqual(lua.globals().released,1)
        self.assertEqual(lua.globals().files['/home/bec_v2/config.dat'],lua.globals().original)

if __name__=='__main__':unittest.main()
