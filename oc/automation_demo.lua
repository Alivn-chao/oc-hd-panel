-- Generic UI example: local simulated telemetry only. Never controls an actual machine.
local component=require("component")
local computer=require("computer")
local event=require("event")
local address
local count=0
for a in component.list("hd_panel",true)do address=a;count=count+1 end
assert(count==1,"请接入一面高清屏幕墙，等待拼屏完成；多面墙时自行指定组件地址")
local p=component.proxy(address)
local running=false
assert(p.setUI({title="自动化控制台",subtitle="通用接口演示 · 数值为样例，不控制真实机器",pages={
  {id="overview",label="运行总览",widgets={
    {id="energy",type="value",x=40,y=124,w=360,h=112,label="电力储备",unit=" EU"},
    {id="rate",type="value",x=420,y=124,w=360,h=112,label="生产速率",unit=" 件/秒"},
    {id="state",type="value",x=800,y=124,w=440,h=112,label="产线状态"},
    {id="stock",type="progress",x=40,y=260,w=1200,h=160,label="产物库存"},
    {id="switch",type="toggle",x=40,y=454,w=300,h=60,label="模拟产线",confirm="切换演示状态？此操作不会控制真实机器。"},
    {id="reset",type="button",x=360,y=454,w=240,h=60,label="重置演示计数"}}},
  {id="help",label="接口说明",widgets={
    {id="helptext",type="text",x=40,y=124,w=1200,h=180,label="接入自己的自动化"}}}
}}))
local amount=2500
local nextUpdate=computer.uptime()+.6
local ok,err=xpcall(function()
  while true do
    if computer.uptime()>=nextUpdate then
      nextUpdate=computer.uptime()+1
      if running then amount=math.min(10000,amount+25)end
      p.updateUI({energy={text="128.4M"},rate={text=running and "25" or "0"},state={text=running and "演示运行中" or "演示已停止"},
        stock={text=amount.." / 10000",value=amount,max=10000},switch={enabled=true,active=running},reset={enabled=true},
        helptext={text="setUI 定义页面；updateUI 更新数据；hd_panel_event 接收点击。"}})
    end
    local e={event.pull(.1)}
    if e[1]=="hd_panel_event"and e[2]==address then
      if e[3]=="switch"then running=e[6]==1;p.ack(e[5],true,"模拟状态已切换")
      elseif e[3]=="reset"then amount=0;p.ack(e[5],true,"演示计数已重置")
      else p.ack(e[5],false,"未定义的控件")end
      nextUpdate=math.min(nextUpdate,computer.uptime()+.6)
    elseif e[1]=="interrupted"or(e[1]=="key_down"and(e[3]==113 or e[3]==81))then break end
  end
end,debug.traceback)
pcall(p.release)
if not ok then error(err)end
