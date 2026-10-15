local files={{path="/home/ochd_bindings.lua",source=[=[-- Strict data-only binding editor shared by the V1 monitor and V2 setup page.
local c=require('component');local fs=require('filesystem');local ser=require('serialization')
local M={}
local function uuid(s)return type(s)=='string'and s:match('^%x%x%x%x%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%x%x%x%x%x%x%x%x$')end
local function read(p)local f=io.open(p,'r');if not f then return end;local s=f:read('*a');f:close();return s end
local function clone(t)return ser.unserialize(ser.serialize(t,false))end
local function field(out,key,label,value,kind)
 out[#out+1]={key=key,label=label,value=value==nil and ''or tostring(value),kind=kind or 'uuid'}
end
function M.revision(text)
 local a,b=1,0;for i=1,#(text or '')do a=(a+text:byte(i))%65521;b=(b+a)%65521 end
 return string.format('%.0f',b*65536+a)
end
function M.parse(payload,fields,revision,draft)
 assert(type(payload)=='string'and #payload<=32768,'绑定数据过大')
 if revision then
  local base,rest=payload:match('^_revision=(%d+)\n(.*)$')
  assert(base==revision and rest,'绑定配置已变化，请重新打开页面');payload=rest
 end
 local specs,values={},{}
 for _,f in ipairs(fields)do specs[f.key]=f.kind end
 for line in (payload..'\n'):gmatch('(.-)\n')do
  local key,v=line:match('^([%w_.-]+)=(.*)$')
  assert(key and specs[key]and values[key]==nil and #v<=160,'字段未知、重复或格式不正确')
  assert(not v:find('[\r\n=]'),'字段不能包含换行或等号')
  local kind=specs[key]
  if v~=''and not draft then
   if kind=='uuid'then assert(uuid(v),'请输入完整组件地址：'..key)
   elseif kind=='path'then assert(v:match('^/[%w_./-]+$')and not v:find('..',1,true),'目录格式不正确')
   elseif kind=='mode'then assert(v=='cell-io'or v=='me-interface','蜂群模式不正确')
   else
    assert(v:match('^%d+$')and tonumber(v)<=2147483647,'数值格式不正确：'..key)
    if kind=='side'then assert(tonumber(v)<=5,'方向必须为0至5')end
   end
  end
  values[key]=v
 end
 for _,f in ipairs(fields)do assert(values[f.key]~=nil,'缺少字段：'..f.key)end
 return values
end
function M.commit(path,value,expected,request)
 assert(read(path)==expected,'配置已被其他程序修改，请重新打开绑定页')
 local suffix=tostring(request);assert(suffix:match('^%d+$'),'保存请求编号无效')
 local temp=path..'.hd.new.'..suffix;local backup=path..'.hd.bak.'..suffix
 assert(not fs.exists(temp)and not fs.exists(backup),'同一保存请求已有文件，请先核对')
 local text=ser.serialize(value,false)
 local f=assert(io.open(temp,'w'));assert(f:write(text));assert(f:flush());f:close()
 assert(read(temp)==text and type(ser.unserialize(text))=='table','临时配置回读失败')
 assert(read(path)==expected,'配置在保存期间改变')
 if expected then assert(fs.rename(path,backup),'原配置备份失败')end
 local ok,reason=pcall(function()assert(fs.rename(temp,path),'配置替换失败');assert(read(path)==text,'保存回读失败')end)
 if not ok then
  if fs.exists(path)then assert(fs.rename(path,path..'.hd.failed.'..suffix))end
  if expected then assert(fs.rename(backup,path),'配置恢复失败')end
  error('保存失败，原配置已恢复：'..tostring(reason))
 end
 return expected and backup or nil
end
function M.v1Fields(cfg)
 local out={};field(out,'node','V1高清监控 · 传送节点',cfg.node);field(out,'field','V1高清监控 · 约束场',cfg.field);return out
end
function M.saveV1(connection,payload,request)
 local path='/home/bec_hd.config';local expected=read(path)
 assert(expected and ser.serialize(ser.unserialize(expected),false)==ser.serialize(connection,false),'高清连接配置已变化')
 local values=M.parse(payload,M.v1Fields(connection),M.revision(expected));assert(uuid(values.node)and uuid(values.field),'两项地址都需要填写')
 assert(c.methods(values.node).getRequiredCondensate~=nil and c.methods(values.field).getStoredCondensate~=nil,'节点或约束场类型不匹配')
 local next=clone(connection);next.node=values.node;next.field=values.field
 return next,M.commit(path,next,expected,request)
end
function M.v2Fields(cfg)
 local out={}
 field(out,'mainInterface','公共 · 主网供液ME接口',cfg.mainInterface)
 field(out,'workshopInterface','公共 · 样板工坊ME接口（可空）',cfg.workshopInterface)
 field(out,'recipeDirectory','公共 · 配方目录',cfg.recipeDirectory,'path')
 field(out,'cacheTarget','公共 · 库存参考目标',cfg.cacheTarget or 144000,'integer')
 for i,g in ipairs(cfg.groups)do
  local prefix='g'..i..'.';local title='组'..i..' · '
  for _,row in ipairs({{'storage','约束场'},{'gate','公共韦门'},{'subInterface','原液子网ME接口'},{'transposer','供液转运器'}})do field(out,prefix..row[1],title..row[2],g[row[1]])end
  field(out,prefix..'mainSide',title..'主网侧方向',g.mainSide or 4,'side');field(out,prefix..'subSide',title..'子网侧方向',g.subSide or 5,'side')
  assert(#g.generators<=16,'本页面最多配置16台纠缠器/组')
  for j=1,math.max(4,#g.generators)do field(out,prefix..'generator'..j,title..'纠缠器'..j..'（多余可空）',g.generators[j])end
  for j=1,math.max(5,#(g.bulkTransposers or {}))do field(out,prefix..'bulk'..j,title..'高速供液口'..j..'（可空）',(g.bulkTransposers or {})[j])end
  local n=g.nanites or {}
  for _,row in ipairs({{'transposer','转运器'},{'loadRedstone','装入红石'},{'returnRedstone','回收红石'}})do
   field(out,prefix..'bee_'..row[1],title..'蜂群 · '..row[2],n[row[1]])
  end
  for _,row in ipairs({{'bankSide',0,'箱子方向'},{'loadSide',3,'装入端口方向'},{'returnSide',2,'回收端口方向'},{'loadOutputSide',0,'装入红石输出方向'},{'returnOutputSide',0,'回收红石输出方向'}})do
   field(out,prefix..'bee_'..row[1],title..'蜂群 · '..row[3],n[row[1]]or row[2],'side')
  end
 end
 local slots={};for i,n in ipairs(cfg.nodes)do slots[n.slot or i]=n end
 for i=1,16 do
  local n=slots[i]or {};local prefix='n'..i..'.';local title='节点'..i..' · '
  field(out,prefix..'address',title..'组件地址（空位不填）',n.address)
  field(out,prefix..'redstone',title..'暂停红石地址',n.redstone)
  field(out,prefix..'pauseSide',title..'暂停红石方向',n.pauseSide or 1,'side')
  field(out,prefix..'group',title..'设备组编号',n.group or 1,'integer')
  field(out,prefix..'gate',title..'独立韦门（空则用组韦门）',n.gate)
 end
 assert(#out<=512,'绑定字段超过页面容量');return out
end
function M.applyV2(cfg,payload,revision)
 local values=M.parse(payload,M.v2Fields(cfg),revision);local next=clone(cfg)
 next.mainInterface=values.mainInterface;next.workshopInterface=values.workshopInterface~=''and values.workshopInterface or nil
 next.recipeDirectory=values.recipeDirectory;next.cacheTarget=tonumber(values.cacheTarget)
 for i,g in ipairs(next.groups)do
  local p='g'..i..'.'
  for _,key in ipairs({'storage','gate','subInterface','transposer'})do g[key]=values[p..key]end
  g.mainSide=tonumber(values[p..'mainSide']);g.subSide=tonumber(values[p..'subSide'])
  g.generators={};for j=1,math.max(4,#cfg.groups[i].generators)do local v=values[p..'generator'..j];if v~=''then g.generators[#g.generators+1]=v end end
  g.bulkTransposers={};for j=1,math.max(5,#(cfg.groups[i].bulkTransposers or {}))do local v=values[p..'bulk'..j];if v~=''then g.bulkTransposers[#g.bulkTransposers+1]=v end end
  local n={mode='cell-io'};g.nanites=n
  for _,key in ipairs({'transposer','loadRedstone','returnRedstone'})do n[key]=values[p..'bee_'..key]~=''and values[p..'bee_'..key]or nil end
  for _,key in ipairs({'bankSide','loadSide','returnSide','loadOutputSide','returnOutputSide'})do n[key]=tonumber(values[p..'bee_'..key])end
  n.enabled=n.transposer~=nil
  assert(n.enabled or not(n.loadRedstone or n.returnRedstone or n.supplyInterface or n.hatchInterface),'蜂群设备需完整填写或一起留空')
 end
 local oldSlots={};for i,n in ipairs(cfg.nodes)do oldSlots[n.slot or i]=n end
 next.nodes={}
 for i=1,16 do
  local p='n'..i..'.';local a,r=values[p..'address'],values[p..'redstone']
  if a~=''or r~=''then
   assert(uuid(a)and uuid(r),'节点组件和暂停红石地址需一起填写')
   local group=tonumber(values[p..'group']);assert(next.groups[group],'节点设备组不存在')
   local row=oldSlots[i]and clone(oldSlots[i])or {name='节点'..i}
   row.slot=i;row.address=a;row.redstone=r;row.group=group;row.pauseSide=tonumber(values[p..'pauseSide'])
   row.gate=values[p..'gate']~=''and values[p..'gate']or next.groups[group].gate
   next.nodes[#next.nodes+1]=row
  end
 end
 return next
end
function M.saveV2(C,cfg,payload,request,expected,revision)
 local function unfinished()
  for i in ipairs(cfg.nodes)do
   local r=C.read(C.path('state/node-'..i..'/production.journal'))
   assert(not r or r.stage=='stopped'and not r.pending and not r.plan,'有未完成生产记录，暂不改绑定')
   for _,move in ipairs(r and r.transfers or {})do assert(move.ok==true,'旧搬运未确认，暂不改绑定')end
   for _,job in ipairs(r and r.requests or {})do assert(job.state=='done','旧申请未确认，暂不改绑定')end
  end
  for i in ipairs(cfg.groups)do
   local path='state/group-'..i..'/'
   for _,name in ipairs({'cohort.dat','nanites-cell.journal','nanites.journal'})do
    local r=C.read(C.path(path..name));assert(not r or r.stage==(name=='cohort.dat'and 'stopped'or 'done'),'有未完成批次或换蜂记录，暂不改绑定')
   end
  end
 end
 unfinished()
 for _,n in ipairs(cfg.nodes)do assert(c.invoke(n.address,'getState')~='crafting','旧绑定仍在生产，先退出控制器')end
 for _,g in ipairs(cfg.groups)do for _,a in ipairs(g.generators)do assert(c.invoke(a,'isWorkAllowed')==false and c.invoke(a,'isMachineActive')==false,'原绑定纠缠器须先停止活动')end end
 local next=M.applyV2(cfg,payload,revision or M.revision(expected));C.validate(next,true)
 for _,n in ipairs(next.nodes)do
  local state=c.invoke(n.address,'getState')
  assert(state=='idle'or(state=='paused-immediate'or state=='nanite-tier-too-low')and c.invoke(n.redstone,'getOutput',n.pauseSide)==15,'先停止生产再改绑定')
 end
 for _,g in ipairs(next.groups)do for _,a in ipairs(g.generators)do assert(c.invoke(a,'isWorkAllowed')==false and c.invoke(a,'isMachineActive')==false,'纠缠器须先停止活动')end end
 unfinished();return next,M.commit(C.path('config.dat'),next,expected,request)
end
-- Drafts preserve raw incomplete input without touching active config or hardware.
function M.draftFields(cfg,record,expected)
 local fields=M.v2Fields(cfg)
 if type(record)=='table'and record.version==1 and record.base==M.revision(expected)and type(record.values)=='table'then
  for _,f in ipairs(fields)do
   local value=record.values[f.key]
   if type(value)=='string'and #value<=160 and not value:find('[\r\n=]')then f.value=value end
  end
 end
 return fields
end
function M.saveDraft(C,cfg,payload,request,expected,rawDraft,revision)
 assert(read(C.path('config.dat'))==expected,'正式配置已变化，请重新打开')
 local values=M.parse(payload,M.v2Fields(cfg),revision,true)
 local path=C.path('bindings.draft.dat')
 local backup=M.commit(path,{version=1,base=M.revision(expected),values=values},rawDraft,request)
 if backup then fs.remove(backup)end
 return read(path)
end
M.read=read
return M
]=]},{path="/home/bec_v2_setup_hd.lua",source=[=[-- A setup-only OCHD page. Never starts production or changes machine outputs.
local c=require('component');local event=require('event');local clock=require('computer')
local C=assert(loadfile('/home/bec_v2/lib/config.lua'))()
local B=assert(loadfile('/home/ochd_bindings.lua'))()
local address,count=nil,0;for a in c.list('hd_panel',true)do address=a;count=count+1 end
assert(count==1,'配置绑定时请只接入一个高清屏幕主组件')
local panel=c.proxy(address)
assert(panel.setPage('bec_dashboard'),'屏幕仍由其他程序占用，请先退出原界面')
local path=C.path('config.dat');local expected=B.read(path)
local cfg=C.read(path)or {version=2,mainInterface='',recipeDirectory='/home/bec_v2/recipes',cacheTarget=144000,
 groups={{name='公共设备组',storage='',gate='',subInterface='',transposer='',mainSide=4,subSide=5,generators={},bulkTransposers={},nanites={enabled=false,mode='cell-io'}}},nodes={}}
local draftPath=C.path('bindings.draft.dat');local rawDraft=B.read(draftPath)
local draft=rawDraft and require('serialization').unserialize(rawDraft)
local function revision()return B.revision((expected or '')..'|'..(rawDraft or ''))end
local notice='可随时暂存草稿，退出查地址后继续；方向可选择，蜂群统一ME-IO。'
local nextPaint=0;local running=true
local ok,err=xpcall(function()
 while running do
  if clock.uptime()>=nextPaint then
   local accepted,reason=panel.update({status='V2 设备配置',stage='setup-only',recipe='设置页 → 设备地址绑定',mode='配置模式',
    controls=false,configEditable=false,bindingEditable=true,bindingDraftEditable=true,bindingRevision=revision(),bindings=B.draftFields(cfg,draft,expected),logs={notice}})
   assert(accepted~=nil,reason);nextPaint=clock.uptime()+(accepted and 2 or 0.6)
  end
  local e={event.pull(0.1)}
  if e[1]=='key_down'and(e[3]==113 or e[3]==81)then running=false end
  if e[1]=='hd_panel_action'and e[2]==address then
   if e[3]=='binding_save'or e[3]=='binding_draft'then
    local applying=e[3]=='binding_save';local submittedRevision=revision()
    local success,result=pcall(function()
     rawDraft=B.saveDraft(C,cfg,e[7],e[5],expected,rawDraft,submittedRevision)
     draft=require('serialization').unserialize(rawDraft)
     if applying then
      cfg=B.saveV2(C,cfg,e[7],e[5],expected,submittedRevision)
      expected=B.read(path)
      -- A completed binding has a new base; old draft is ignored until replaced.
      notice='绑定已应用；退出配置页后再启动V2。'
     else notice='草稿已暂存，可退出查地址，回来继续填写。'end
    end)
    if not success then notice=tostring(result)..'（已成功暂存的内容会保留）'end
    -- Carry the new compare-and-swap revision in the acknowledgement.
    panel.ack(e[5],success,'[rev:'..revision()..'] '..notice);nextPaint=0
   elseif e[3]=='refresh'then panel.ack(e[5],true,'配置页在线');nextPaint=clock.uptime()+0.6
   else panel.ack(e[5],false,'此页面只配置地址，不启动或控制生产')end
  end
 end
end,debug.traceback)
pcall(panel.release)
assert(ok,err)
]=]},{path="/home/bec_hd.lua",source=[=[-- OC HD Panel adapter. Existing bec_ui.lua remains responsible for machine writes.
local mode, panelAddress = ...
mode = mode or "monitor"
assert(mode == "monitor" or mode == "run" or mode == "resume" or mode == "restock",
  "用法: bec_hd.lua monitor|run|resume|restock [屏幕地址]")
local component = require("component")
local computer = require("computer")
local event = require("event")
local serialization = require("serialization")
local filesystem = require("filesystem")
local address
if panelAddress then address = assert(component.get(panelAddress), "屏幕地址不存在")
else
  local count = 0
  for a in component.list("hd_panel", true) do address = a; count = count + 1 end
  assert(count == 1, "请接入一块高清屏幕；多块屏幕时指定地址")
end
assert(component.type(address) == "hd_panel", "指定组件不是高清屏幕")
local panel = component.proxy(address)
assert(panel.setPage("bec_dashboard"))
local configFile=assert(io.open("/home/bec_hd.config","r"),"请从 bec_hd.config.example 创建 /home/bec_hd.config 并填写组件地址")
local configRaw=configFile:read("*a");configFile:close()
local configured,connection=pcall(serialization.unserialize,configRaw)
assert(configured and type(connection)=="table" and connection.version==1,"高清面板连接配置无效")
local function uuid(value)
  return type(value)=="string" and value:match("^%x%x%x%x%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%x%x%x%x%x%x%x%x$")
end
assert(uuid(connection.node) and uuid(connection.field),"请填写完整的 BEC 节点 / 约束场组件地址")
local node,field=connection.node,connection.field
local bindings
if filesystem.exists('/home/ochd_bindings.lua')then bindings=assert(loadfile('/home/ochd_bindings.lua'))()end
local originalPull = event.pull
local originalPrint = print
local started, nextSnapshot = computer.uptime(), 0
local stopping, exiting, active = false, false, mode ~= "monitor"
local pauseRequest
local recent, lastStage = {}, nil
local labels = {
  idle="等待订单", crafting="生产中", ["paused-immediate"]="立即暂停",
  ["nanite-tier-too-low"]="蜂群等级不足", ["assembler-offline"]="等待观测阵列", unpowered="等待供电"
}
local fluidLabels = {
  entangled_bedrockium="基岩锭", entangled_cosmicneutronium="宇宙中子素",
  entangled_neutronium="中子素", entangled_chromaticglass="彩色玻璃",
  entangled_transcendentmetal="超时空金属", entangled_infinity="无尽",
  entangled_dimshiftedsuperfluid="维度偏移超流体",
  entangled_infinitycatalyst="无尽催化剂", entangled_spacetime="时空",
  entangled_eternity="永恒", entangled_hypogen="海珀珍", entangled_shirabon="shirabon"
}
local function read(address, method)
  local ok, value = pcall(component.invoke, address, method)
  return ok and value or nil
end
local function journal(path)
  local f = io.open(path, "r")
  if not f then return {} end
  local raw = f:read("*a"); f:close()
  local ok, value = pcall(serialization.unserialize, raw)
  return ok and type(value) == "table" and value or {}
end
local function log(text)
  recent[#recent + 1] = tostring(text)
  while #recent > 8 do table.remove(recent, 1) end
end
local function reply(e, success, message)
  if type(e[5])=="number" and panel.ack then pcall(panel.ack,e[5],success,message) end
  log(message)
end
local function checkConfigRecovery()
  if filesystem.exists("/home/bec_cache.config") then return end
  for name in filesystem.list("/home") do
    assert(not name:match("^bec_cache%.config%.hd%.bak%."),"缓存配置缺失；请从 /home/bec_cache.config.hd.bak.* 恢复原配置后再启动")
  end
end
local function saveTarget(value, request)
  assert(type(value)=="number" and value>0 and value<=2147483647 and value==math.floor(value),"默认目标必须为有效正整数")
  checkConfigRecovery()
  local path="/home/bec_cache.config"
  local temp=path..".hd.new."..tostring(request)
  local backup=path..".hd.bak."..tostring(request)
  assert(not filesystem.exists(temp) and not filesystem.exists(backup),"临时文件已存在，请核对后重试")
  local config={version=1,overrides={}}
  local old=io.open(path,"r")
  local existed=old~=nil
  if old then
    local raw=assert(old:read("*a"));old:close()
    local parsed=serialization.unserialize(raw)
    assert(type(parsed)=="table" and parsed.version==1 and type(parsed.target)=="number" and parsed.target>0
      and parsed.target<=2147483647 and parsed.target==math.floor(parsed.target)
      and type(parsed.overrides or {})=="table","原缓存配置无效，未修改")
    for name,n in pairs(parsed.overrides or {}) do
      assert(type(name)=="string" and type(n)=="number" and n>=0 and n<=2147483647 and n==math.floor(n),"原单项覆盖值无效，未修改")
    end
    config=parsed
  else assert(not filesystem.exists(path),"无法读取原配置，未修改") end
  config.target=value;config.overrides=config.overrides or {}
  local raw=serialization.serialize(config,false)
  local file=assert(io.open(temp,"w"))
  local wrote,writeReason=file:write(raw)
  local flushed,flushReason=file:flush()
  local closed,closeResult,closeReason=pcall(file.close,file)
  assert(wrote,writeReason);assert(flushed,flushReason)
  assert(closed and closeResult~=false and not closeReason,"临时文件关闭失败") -- OC close() succeeds with no return value.
  local check=assert(io.open(temp,"r"));local actual=assert(check:read("*a"));check:close()
  assert(actual==raw and serialization.unserialize(actual).target==value,"临时配置读回核对失败，未修改原配置")
  if existed then assert(filesystem.rename(path,backup),"无法备份原配置，未修改") end
  local called,renamed,reason=pcall(filesystem.rename,temp,path)
  if not called or not renamed then
    if existed then assert(filesystem.rename(backup,path),"保存失败且回滚失败；原配置位于 "..backup) end
    error("保存失败，原配置已恢复："..tostring(reason))
  end
  return existed and backup or nil
end
local function publish(force)
  local now = computer.uptime()
  if not force and now < nextSnapshot then return end
  nextSnapshot = now + 2 -- No AE network scans; hardware reads only once every 2 seconds.
  local record = journal("/home/bec_auto.journal")
  if record.stage and record.stage~=lastStage then
    log("生产阶段："..tostring(record.stage));lastStage=record.stage
  end
  local cache = journal("/home/bec_cache.journal")
  local config = journal("/home/bec_cache.config")
  local stock = read(field, "getStoredCondensate")
  local state = read(node, "getState")
  local tier = read(node, "getProvidedTier")
  local required = read(node, "getRequiredTier")
  local target = record.target or cache.target or {}
  if type(stock)=="table" then
    target={}
    local goal=tonumber(config.target) or 144000
    for name in pairs(stock) do
      local override=type(config.overrides)=="table" and config.overrides[name] or nil
      target[name]=type(override)=="number" and override>=0 and override==math.floor(override)
        and override<=2147483647 and override or goal
    end
  end
  local rows, names, achieved, total = {}, {}, 0, 0
  if type(stock) == "table" then for name in pairs(stock) do names[name] = true end end
  for name in pairs(target) do names[name] = true end
  local sorted = {}; for name in pairs(names) do sorted[#sorted+1]=name end; table.sort(sorted)
  for _, name in ipairs(sorted) do
    if #rows >= 32 then break end
    local amount, goal = tonumber(type(stock)=="table" and stock[name]) or 0, tonumber(target[name]) or 0
    rows[#rows + 1] = {name=fluidLabels[name] or name:gsub("^entangled_", ""), amount=amount, target=goal}
    if goal > 0 then total=total+1; if amount>=goal then achieved=achieved+1 end end
  end
  local snapshot = {
    status=labels[state] or tostring(state or "机器读取失败"),
    stage=tostring(record.stage or "尚无生产日志"), recipe="BEC 凝聚装配生产",
    mode=mode=="monitor" and "只读监控" or (active and "常驻服务" or "服务已停止，可恢复"),
    controls=mode~="monitor", configEditable=mode~="monitor" and not active,
    bindingEditable=bindings~=nil and not active,bindingRevision=bindings and bindings.revision(configRaw)or '',bindings=bindings and bindings.v1Fields(connection)or {},
    configTarget=tonumber(config.target) or 144000, parallel=read(node, "getParallelRecipesInProgress") or 0,
    maxParallel=read(node,"getMaxParallel") or 0, naniteTier=type(tier)=="table" and tier.tier or 0,
    requiredTier=type(required)=="table" and required.tier or 0,
    naniteCount=read(node,"getAvailableNanites") or 0,
    cachePercent=total>0 and achieved/total*100 or 0, uptime=now-started, fluids=rows, logs=recent
  }
  local ok, result, reason = pcall(panel.update, snapshot)
  if not ok then originalPrint("高清屏幕更新失败：" .. tostring(result))
  elseif result==nil then originalPrint("高清屏幕拒绝更新："..tostring(reason)) end
end
local function action(e)
  if e[1] ~= "hd_panel_action" or e[2] ~= address then return e end
  if e[3] == "refresh" then nextSnapshot=0;reply(e,true,"已请求刷新，等待下一份数据");return {} end
  if e[3]=='binding_save'then
    if active or not bindings then reply(e,false,'先暂停服务，再保存高清监控绑定');return {}end
    local ok,next,backup=pcall(bindings.saveV1,connection,e[7],e[5])
    if ok then connection=next;configRaw=bindings.read('/home/bec_hd.config');node,field=connection.node,connection.field;nextSnapshot=0 end
    reply(e,ok,ok and '高清监控地址已保存；生产控制器绑定保持原样' or tostring(next))
    return {}
  end
  if mode == "monitor" then reply(e,false,"只读监控拒绝控制操作");return {} end
  if e[3]=="cache_target" then
    if active then reply(e,false,"请先暂停原控制器，再保存目标");return {} end
    local ok,result=pcall(saveTarget,e[6],e[5])
    reply(e,ok,ok and "默认目标已保存；新补库计划读取，未完成计划按原日志恢复" or "保存失败："..tostring(result))
    if ok and result then log("原配置备份："..result) end
    nextSnapshot=0;return {}
  end
  if e[3] == "pause" and active then stopping=true;pauseRequest=e;return {"key_down","hd_panel",113,16}
  elseif e[3] == "patterns" and active then reply(e,true,"样板请求已交给原控制器，结果见日志");return {"key_down","hd_panel",115,31}
  elseif e[3] == "resume" and active then reply(e,true,"重试请求已交给原控制器，结果见日志");return {"key_down","hd_panel",116,20}
  elseif e[3] == "resume" and not active then return {"hd_resume",e} end
  reply(e,false,"服务已经停止，此操作当前不可用")
  return {}
end
local function bridgePull(timeout, ...)
  -- Preserve event.pull filters: the current bec_ui scheduler calls its saved pull without a filter.
  publish(false)
  local e = {originalPull(timeout, ...)}
  if e[1]=="key_down" and (e[3]==113 or e[3]==81) then exiting=true end
  return table.unpack(action(e))
end
local function runUI(runMode)
  stopping=false; active=runMode~="monitor"
  checkConfigRecovery()
  local ui = assert(loadfile("/home/bec_ui.lua", "t", _ENV), "缺少现有 /home/bec_ui.lua")
  event.pull=bridgePull
  local ok, err = xpcall(function() ui(runMode) end, debug.traceback)
  event.pull=originalPull; active=false
  if pauseRequest then reply(pauseRequest,ok,ok and "原控制界面已停止，适配器仍保持连接" or "控制界面异常退出，请核对日志");pauseRequest=nil end
  if not ok then log("控制界面退出："..tostring(err)); originalPrint(err) end
  log(stopping and "已通过原控制器暂停服务" or "控制界面已返回")
  publish(true)
end
local ok, err = xpcall(function()
  log("高清面板已连接；刷新周期 2 秒")
  if mode~="monitor" then runUI(mode) end
  while not exiting do
    publish(false)
    local e = action({originalPull(.25)})
    if e[1]=="hd_resume" then
      local record=journal("/home/bec_auto.journal")
      local f=io.open("/home/bec_auto.journal","r");local exists=f~=nil;if f then f:close() end
      local resumeMode=exists and record.stage~="stopped" and "resume" or "run"
      reply(e[2],true,"已交给原控制器的 "..resumeMode.." 入口，结果见运行状态");runUI(resumeMode)
    elseif e[1]=="key_down" and (e[3]==113 or e[3]==81) then exiting=true end
  end
end, debug.traceback)
event.pull=originalPull; print=originalPrint
pcall(panel.release)
if not ok then originalPrint(err) end
]=]}}

local fs=require('filesystem')
local function read(p)local f=io.open(p,'r');if not f then return end;local s=f:read('*a');f:close();return s end
local originals={};local identical=true
for i,row in ipairs(files)do
 assert(load(row.source,'@'..row.path,'t',_ENV),'Source syntax failed')
 originals[i]=read(row.path)or false
 if originals[i]~=row.source then identical=false end
end
if identical then print('ALREADY INSTALLED: OCHD binding scripts');return end
local n=1
while fs.exists('/home/ochd_bindings.lua.before-binding-'..n)or fs.exists('/home/ochd_bindings.lua.binding-new-'..n)do n=n+1 end
for _,row in ipairs(files)do
 assert(not fs.exists(row.path..'.before-binding-'..n)and not fs.exists(row.path..'.binding-new-'..n),'Backup collision')
 local p=row.path..'.binding-new-'..n;local f=assert(io.open(p,'w'));assert(f:write(row.source));assert(f:flush());f:close();assert(read(p)==row.source,'Read-back failed')
end
for i,row in ipairs(files)do assert((read(row.path)or false)==originals[i],'Source changed during installation')end
local retired,installed={},{}
local ok,err=pcall(function()
 for i,row in ipairs(files)do
  if originals[i]then assert(fs.rename(row.path,row.path..'.before-binding-'..n));retired[i]=true end
  assert(fs.rename(row.path..'.binding-new-'..n,row.path));installed[i]=true
  assert(read(row.path)==row.source,'Installed verification failed')
 end
end)
if not ok then
 for i=#files,1,-1 do local p=files[i].path
  if installed[i]then assert(fs.rename(p,p..'.binding-failed-'..n))end
  if retired[i]then assert(fs.rename(p..'.before-binding-'..n,p))end
 end
 error('Installation failed; original scripts restored: '..tostring(err))
end
print('INSTALLED: OCHD binding scripts. No configuration, recipe or production journal changed.')
print('Requires matching 0.3.2-alpha4-drafts mod on BOTH client and server.')
print('V2 setup page: lua /home/bec_v2_setup_hd.lua')
print('V1 monitoring bindings: lua /home/bec_hd.lua monitor')
