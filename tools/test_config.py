"""Fault-injected Lua configuration transactions. Uses in-memory files, never game hardware."""
from pathlib import Path
from lupa import LuaRuntime
source=(Path(__file__).resolve().parents[1]/'oc/bec_hd.lua').read_text(encoding='utf-8')
harness=r'''
local case=...
local path="/home/bec_cache.config"
local files={["/home/bec_hd.config"]="connection",[path]="cfg:144000",["/home/bec_auto.journal"]="journal"}
if case=="malformed" then files[path]="bad" end
if case=="recovery" then files[path]=nil;files[path..".hd.bak.1"]="cfg:144000" end
local original=files[path]
local starts,releases,acks,snapshots=0,0,{},{}
local queue
if case=="readonly" then queue={{"hd_panel_action","panel","cache_target","player",14,5000000},
 {"hd_panel_action","panel","refresh","player",15,0},{"key_down","k",113,16}}
else queue={{"hd_panel_action","panel","cache_target","player",11,5000000},
 {"hd_panel_action","panel","pause","player",12,0},
 {"hd_panel_action","panel","cache_target","player",13,0},
 {"hd_panel_action","panel","cache_target","player",14,5000000},{"key_down","k",113,16}} end
local failedOnce=false
local fs={exists=function(p)return files[p]~=nil end,
 list=function(dir)
  local names={};for p in pairs(files)do if p:sub(1,6)=="/home/"then names[#names+1]=p:sub(7)end end
  local i=0;return function()i=i+1;return names[i]end
 end,
 rename=function(a,b)
  if a:find(".hd.new.",1,true) and b==path and (case=="rename" or case=="throw") and not failedOnce then
   failedOnce=true;if case=="throw"then error("injected rename exception")end;return nil,"injected rename failure"
  end
  if not files[a] or files[b] then return nil,"bad rename" end
  files[b]=files[a];files[a]=nil;return true
 end}
local io={open=function(p,mode)
 if mode=="w"then
  local body=""
  return {write=function(self,s)body=s;return self end,
   flush=function(self)if case=="flush"then return nil,"injected disk full"end;files[p]=body;return self end,
   close=function()end} -- OC component.close() returns nil on success.
 end
 if files[p]==nil then return nil end
 return {read=function()
  if case=="readback" and p:find(".hd.new.",1,true)then return "corrupt"end
  return files[p]end,close=function()end}
end}
local ser={unserialize=function(raw)
 if raw=="connection"then return {version=1,node="00000000-0000-0000-0000-000000000001",field="00000000-0000-0000-0000-000000000002"} elseif raw=="journal"then return {stage="stopped"}end
 local n=raw:match("^cfg:(%d+)$")
 if n then return {version=1,target=tonumber(n),overrides={entangled_infinity=0},extra="preserved"}end
 error("invalid serialization")
end,serialize=function(config,pretty)
 assert(pretty==false,"must disable formatting")
 assert(config.overrides.entangled_infinity==0 and config.extra=="preserved","existing settings lost")
 return "cfg:"..tostring(config.target)
end}
local panel={setPage=function()return true end,release=function()releases=releases+1 end,
 update=function(s)snapshots[#snapshots+1]=s;return true end,
 ack=function(id,ok,message)acks[id]={ok=ok,message=message};return true end}
local comp={get=function(a)return a end,type=function()return "hd_panel"end,proxy=function()return panel end,
 list=function()local done=false;return function()if not done then done=true;return "panel"end end end,
 invoke=function(a,m)
  if m=="getStoredCondensate"then return {entangled_chromaticglass=1}
  elseif m=="getState"then return "idle"
  elseif m=="getProvidedTier" or m=="getRequiredTier"then return {tier=4}else return 0 end
 end}
local time=0
local event={pull=function()time=time+.5;local e=table.remove(queue,1);assert(e,"queue exhausted");return table.unpack(e)end}
local oldPull=event.pull
local env=setmetatable({io=io},{__index=_G})
env.require=function(name)
 if name=="component"then return comp elseif name=="event"then return event elseif name=="filesystem"then return fs
 elseif name=="serialization"then return ser elseif name=="computer"then return {uptime=function()return time end}else error(name)end
end
env.print=function()end
env.loadfile=function()
 return function()
  starts=starts+1
  while true do local e={event.pull(.25)};if e[1]=="key_down" and e[3]==113 then return end end
 end
end
assert(load(SOURCE,"adapter","t",env))(case=="readonly" and "monitor" or "run")
assert(releases==1 and oldPull==event.pull,"cleanup failure")
if case=="recovery" then assert(starts==0,"started controller with missing configuration backup")
elseif case=="readonly"then
 assert(starts==0 and not acks[14].ok and acks[15].ok,"read-only control guard failed")
 assert(files[path]==original,"readonly changed file")
else
 assert(starts==1 and not acks[11].ok,"active controller changed configuration")
 assert(acks[12].ok,"pause not acknowledged")
 assert(not acks[13].ok,"zero default target accepted")
 if case=="success"then
  assert(acks[14].ok,"successful save rejected")
  assert(files[path]=="cfg:5000000" and files[path..".hd.bak.14"]==original,"save/backup missing")
  assert(snapshots[#snapshots].configEditable and snapshots[#snapshots].configTarget==5000000,"new settings not published")
 else
  assert(not acks[14].ok,"injected failure reported success")
  assert(files[path]==original,"original configuration lost")
 end
end
return case
'''
for case in ['success','readonly','flush','readback','rename','throw','malformed','recovery']:
    lua=LuaRuntime(unpack_returned_tuples=True)
    lua.globals().SOURCE=source
    print('config transaction:',lua.execute(harness,case),'PASS')
