"""Mocked generic app demo: validates UI declarations, events and cleanup, no machine access."""
from pathlib import Path
from lupa import LuaRuntime
source=(Path(__file__).resolve().parents[1]/'oc/automation_demo.lua').read_text(encoding='utf-8')
lua=LuaRuntime(unpack_returned_tuples=True)
lua.globals().SOURCE=source
lua.execute(r'''
local time,published,released=0,{},0
local acks={}
local queue={{},{"hd_panel_event","wall","switch","player",71,1},
 {"hd_panel_event","wall","reset","player",72,0},{"key_down","k",113,16}}
local p={setUI=function(layout)
 assert(layout.title=="自动化控制台"and #layout.pages==2)
 assert(layout.pages[1].widgets[5].type=="toggle")
 return true
end,updateUI=function(values)published[#published+1]=values;return true end,
 ack=function(id,ok,message)acks[id]=ok end,release=function()released=released+1 end}
local comp={proxy=function()return p end,list=function()local once=false;return function()if not once then once=true;return "wall"end end end}
local env=setmetatable({},{__index=_G})
env.require=function(name)
 if name=="component"then return comp elseif name=="computer"then return {uptime=function()return time end}
 elseif name=="event"then return {pull=function()time=time+1;local e=table.remove(queue,1);assert(e,"queue exhausted");return table.unpack(e)end}
 else error("unexpected module or machine access: "..name)end
end
assert(load(SOURCE,"generic-demo","t",env))()
assert(released==1 and acks[71] and acks[72])
assert(#published==3)
assert(published[1].state.text=="演示已停止")
assert(published[2].rate.text=="25"and published[2].switch.active)
-- Reset clears the counter; the next running sample adds 25 again.
assert(published[3].stock.value==25 and published[3].switch.active)
''')
print('Generic app declaration, toggle/reset events and cleanup: PASS')
