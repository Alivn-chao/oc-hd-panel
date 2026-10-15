-- Strict data-only binding editor shared by the V1 monitor and V2 setup page.
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
