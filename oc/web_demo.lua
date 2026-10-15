-- Demonstrates real OC metrics and round-trip UI events. Never controls machines.
local c=require('component');local event=require('event');local computer=require('computer')
local W=assert(loadfile('/home/ochd_web.lua'))();local address,count=nil,0
for a in c.list('hd_panel',true)do address=a;count=count+1 end
assert(count==1,'示例请只接入一块OCHD墙的主组件')
local panel=c.proxy(address);assert(type(panel.setWebApp)=='function'and type(panel.publish)=='function','请先更新客户端和服务器的HTML原型JAR');local package=W.package('/home/ochd_apps/demo')
while true do local ok,reason=panel.setWebApp(package);assert(ok~=nil,reason);if ok then break end;event.pull(0.6)end
print('HTML示例已上传。右键OCHD进行交互，OC键盘Q退出。示例不控制机器。')
local target,counter,nextPush=144000,0,0
local ok,reason=xpcall(function()
 local running=true
 while running do
  if computer.uptime()>=nextPush then
   counter=counter+1;local accepted,message=panel.publish({uptime=computer.uptime(),freeMemory=computer.freeMemory(),totalMemory=computer.totalMemory(),target=target,counter=counter})
   assert(accepted~=nil,message);nextPush=computer.uptime()+(accepted and 1 or 0.6)
  end
  local e={event.pull(0.05)}
  if e[1]=='key_down'and(e[3]==113 or e[3]==81)then running=false end
  if e[1]=='hd_panel_web'and e[2]==address then
   local success,message=pcall(function()
    local args=W.decode(e[6]);assert(type(args)=='table','无效事件数据')
    if e[3]=='set_target'then local n=args.target;assert(type(n)=='number'and n==math.floor(n)and n>=1 and n<=1000000,'目标须为1到1000000的整数');target=n;return '目标已由OC更新：'..n
    elseif e[3]=='reset_counter'then counter=0;return '演示计数已清零'end
    error('未知事件')
   end)
   panel.ack(e[5],success,tostring(message));nextPush=computer.uptime()+0.6
  end
 end
end,debug.traceback)
pcall(panel.release);assert(ok,reason)
