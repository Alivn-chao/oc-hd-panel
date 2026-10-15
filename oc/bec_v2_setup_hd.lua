-- A setup-only OCHD page. Never starts production or changes machine outputs.
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
local notice='打开设置页 → 设备地址绑定；支持 Ctrl+V 粘贴。保存不会启动生产。'
local nextPaint=0;local running=true
local ok,err=xpcall(function()
 while running do
  if clock.uptime()>=nextPaint then
   local accepted,reason=panel.update({status='V2 设备配置',stage='setup-only',recipe='设置页 → 设备地址绑定',mode='配置模式',
    controls=false,configEditable=false,bindingEditable=true,bindingRevision=B.revision(expected),bindings=B.v2Fields(cfg),logs={notice}})
   assert(accepted~=nil,reason);nextPaint=clock.uptime()+(accepted and 2 or 0.6)
  end
  local e={event.pull(0.1)}
  if e[1]=='key_down'and(e[3]==113 or e[3]==81)then running=false end
  if e[1]=='hd_panel_action'and e[2]==address then
   if e[3]=='binding_save'then
    local success,result,backup=pcall(B.saveV2,C,cfg,e[7],e[5],expected)
    if success then cfg=result;expected=B.read(path);notice='绑定配置已保存；退出配置页后再正常启动V2。'else notice=tostring(result)end
    panel.ack(e[5],success,notice);nextPaint=clock.uptime()+0.6
   elseif e[3]=='refresh'then panel.ack(e[5],true,'配置页在线');nextPaint=clock.uptime()+0.6
   else panel.ack(e[5],false,'此页面只配置地址，不启动或控制生产')end
  end
 end
end,debug.traceback)
pcall(panel.release)
assert(ok,err)
