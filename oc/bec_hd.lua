-- OC HD Panel adapter. Existing bec_ui.lua remains responsible for machine writes.
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
