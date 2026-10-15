-- Independent OCHD compatibility page. Virtual balances only; no machine control.
local c=require('component');local event=require('event');local computer=require('computer')
local W=assert(loadfile('/home/ochd_web.lua'))();local address,count=nil,0
for a in c.list('hd_panel',true)do address=a;count=count+1 end
assert(count==1,'请只接入一块OCHD墙的主组件')
local methods=assert(c.methods(address));assert(methods.setWebApp~=nil and methods.publish~=nil,'需要OCHD HTML版本')
local panel=c.proxy(address);local package=W.package('/home/ochd_apps/defi-yield')
while true do local ok,reason=panel.setWebApp(package);assert(ok~=nil,reason);if ok then break end;event.pull(0.6)end
local connected,balance,receipts,nextPush=false,10000,0,0
local pools={['eth-usdc']=true,['usdc-usdt']=true,['wbtc-eth']=true,['neon-usdc']=true,['dai-usdc']=true,['eth-neon']=true}
print('NEON YIELD ready. All balances are virtual. Right-click OCHD; Q here to exit.')
local ok,reason=xpcall(function()
 while true do
  if computer.uptime()>=nextPush then
   local accepted,message=panel.publish({uptime=computer.uptime(),freeMemory=computer.freeMemory(),totalMemory=computer.totalMemory(),balance=balance,connected=connected,receipts=receipts})
   assert(accepted~=nil,message);nextPush=computer.uptime()+(accepted and 1 or 0.6)
  end
  local e={event.pull(0.05)}
  if e[1]=='key_down'and(e[3]==113 or e[3]==81)then break end
  if e[1]=='hd_panel_web'and e[2]==address then
   local success,message=pcall(function()
    local args=W.decode(e[6]);assert(type(args)=='table','Invalid request')
    if e[3]=='demo_connect'then connected=true;receipts=receipts+1;return 'OC: demo account connected'
    elseif e[3]=='demo_disconnect'then connected=false;receipts=receipts+1;return 'OC: demo account disconnected'
    elseif e[3]=='demo_deposit'then
     local n=args.amount;assert(connected,'Connect the demo account first');assert(pools[args.pool],'Unknown pool')
     assert(type(n)=='number'and n==n and n>0 and n<=balance and math.abs(n*100-math.floor(n*100+0.5))<0.00001,'Invalid amount or insufficient virtual balance')
     balance=math.floor((balance-n)*100+0.5)/100;receipts=receipts+1;return 'OC: virtual deposit accepted; no real assets moved'
    elseif e[3]=='echo_ping'then receipts=receipts+1;return 'OC round trip OK / receipt '..receipts end
    error('Unknown event')
   end)
   panel.ack(e[5],success,tostring(message));nextPush=0
  end
 end
end,debug.traceback)
pcall(function()panel.release()end);assert(ok,reason)
