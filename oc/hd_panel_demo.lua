-- Display-only demo: never accesses BEC, AE or machine controls.
local component = require("component")
local event = require("event")
local panel = component.hd_panel
assert(panel, "请接入一块 hd_panel 高清面板")
assert(panel.setPage("bec_dashboard"))
local start = require("computer").uptime()
local fluids={}
for i=1,19 do fluids[i]={name="演示凝聚物 "..i,amount=i*208900,target=5000000} end
local ok, err = xpcall(function()
  repeat
    panel.update({status="演示数据",stage="界面演示",recipe="高清屏幕显示测试 · 不控制机器",mode="演示",
      parallel=64,maxParallel=64,naniteTier=4,naniteCount=19612,requiredTier=4,cachePercent=30,
      progress=440,progressMax=800,uptime=require("computer").uptime()-start,controls=false,
      configEditable=false,configTarget=5000000,fluids=fluids,
      logs={"这是显示演示，数值为样例", "中文字体 / 卡片 / 720p 纹理测试", "按 Q 退出演示"}})
    local e = {event.pull(1)}
    if e[1]=="hd_panel_action" and e[2]==panel.address and e[3]=="refresh" then
      panel.ack(e[5],true,"演示刷新已收到；所有数值均为样例")
    end
    if e[1]=="interrupted" or (e[1]=="key_down" and (e[3]==113 or e[3]==81)) then break end
  until false
end, debug.traceback)
pcall(panel.release)
if not ok then error(err) end
