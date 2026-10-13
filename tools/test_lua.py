"""Exercise the adapter with a mocked existing UI; no hardware access."""
from pathlib import Path
from lupa import LuaRuntime

root = Path(__file__).resolve().parents[1]
for path in (root / 'oc').glob('*.lua'):
    lua = LuaRuntime(unpack_returned_tuples=True)
    chunk, error = lua.eval('function(s) local f,e=load(s); return f,e end')(path.read_text(encoding='utf-8'))
    assert chunk is not None, (path, error)
print('All OC scripts compile in Lua')

source = (root / 'oc/bec_hd.lua').read_text(encoding='utf-8')
harness = r'''
local mode=...
local time, releases, reads, starts = 0,0,0,{}
local received, snapshots={},{}
local queue={}
if mode=="monitor" then queue={{"hd_panel_action","panel","pause"},{"key_down","k",113,16}}
elseif mode=="run" then queue={{"hd_panel_action","other","pause"},{"hd_panel_action","panel","pause"},
 {"hd_panel_action","panel","resume"},{"key_down","k",113,16}}
elseif mode=="resume" then queue={{"hd_panel_action","panel","resume"},{"hd_panel_action","panel","pause"},
 {"hd_panel_action","panel","resume"},{"key_down","k",113,16}}
else queue={{"key_down","k",113,16}} end
local panel={ack=function()return true end,setPage=function() return true end,update=function(s) snapshots[#snapshots+1]=s;return true end,
 release=function() releases=releases+1 end}
local component={get=function(a)return a end,type=function()return "hd_panel" end,proxy=function()return panel end,
 list=function()local done=false; return function()if not done then done=true;return "panel" end end end,
 invoke=function(a,m)
  reads=reads+1
  if m=="getState" then return "crafting" elseif m=="getStoredCondensate" then return {entangled_chromaticglass=2500000}
  elseif m=="getProvidedTier" or m=="getRequiredTier" then return {tier=4}
  elseif m=="getAvailableNanites" then return 19612 elseif m=="getMaxParallel" then return 64 else return 32 end
 end}
local event={pull=function()time=time+.5; local e=table.remove(queue,1); if not e then error("queue exhausted") end;return table.unpack(e) end}
local env=setmetatable({},{__index=_G})
env.require=function(name)
 if name=="component"then return component elseif name=="computer"then return {uptime=function()return time end}
 elseif name=="filesystem"then return {exists=function(path)return path=="/home/bec_cache.config"end,list=function()return function()end end}
 elseif name=="event"then return event elseif name=="serialization"then return {unserialize=function(raw)
  if raw=="connection"then return {version=1,node="00000000-0000-0000-0000-000000000001",field="00000000-0000-0000-0000-000000000002"} elseif raw=="config"then return {version=1,target=5000000} else return {stage=mode=="resume" and "watching" or "stopped"} end end}
 else error(name) end
end
env.io={open=function(path)
 if path=="/home/bec_hd.config"then return {read=function()return "connection"end,close=function()end} elseif path=="/home/bec_cache.config"then return {read=function()return "config"end,close=function()end}
 elseif path=="/home/bec_auto.journal"then return {read=function()return "journal"end,close=function()end} end end}
env.loadfile=function(path)
 assert(path=="/home/bec_ui.lua")
 return function(startMode)
  starts[#starts+1]=startMode
  while true do
   local e={event.pull(.25)};received[#received+1]=e
   if e[1]=="key_down" and e[3]==113 then return end
  end
 end
end
env.print=function()end
local oldPull=event.pull
local f=assert(load(SOURCE,"adapter","t",env)); f(mode)
assert(releases==1,"panel binding was not released")
assert(event.pull==oldPull,"event.pull was not restored")
assert(#snapshots>=1 and snapshots[1].maxParallel==64)
assert(snapshots[1].fluids[1].target==5000000)
if mode=="monitor" then
 assert(#starts==0,"monitor started the original UI/controller")
 assert(snapshots[1].controls==false)
else
 assert(#starts==2 and starts[1]==mode and starts[2]==mode,"wrong recovery entry point")
 if mode=="run" then assert(received[1][1]=="hd_panel_action" and received[1][2]=="other","foreign event misrouted")
 else assert(received[1][1]=="key_down" and received[1][3]==116,"fault retry did not use existing T key") end
 assert(received[2][1]=="key_down" and received[2][3]==113,"pause did not use original UI quit path")
end
return reads
'''
for mode in ['monitor', 'run', 'resume']:
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.globals().SOURCE = source
    reads = lua.execute(harness, mode)
    print(f'{mode}: event routing, read-only guard, config target, resume and cleanup pass ({reads} reads)')
