local files={{path=[=[/home/ochd_web.lua]=],text=[=[-- HTML package loader and bounded JSON decoder. Never evaluates page-provided Lua.
local fs=require('filesystem');local unicode=require('unicode')
local M={null={}}
function M.decode(text)
 assert(type(text)=='string'and #text<=262144,'JSON too large')
 local pos,nodes=1,0
 local function ws()local _,last=text:find('^[ \t\r\n]*',pos);pos=(last or pos-1)+1 end
 local value
 local function str()
  assert(text:sub(pos,pos)=='"','Expected JSON string');pos=pos+1;local out={}
  while pos<=#text do
   local ch=text:sub(pos,pos);pos=pos+1
   if ch=='"'then return table.concat(out)end
   if ch=='\\'then
    local e=text:sub(pos,pos);pos=pos+1
    local escapes={['"']='"',['\\']='\\',['/']='/',b='\b',f='\f',n='\n',r='\r',t='\t'}
    if e=='u'then
     local hex=text:sub(pos,pos+3);assert(hex:match('^%x%x%x%x$'),'Invalid Unicode escape');pos=pos+4;local code=tonumber(hex,16)
     if code>=0xD800 and code<=0xDBFF then
      assert(text:sub(pos,pos+1)=='\\u','Missing Unicode surrogate');local tail=text:sub(pos+2,pos+5);assert(tail:match('^%x%x%x%x$'),'Invalid surrogate');local low=tonumber(tail,16);assert(low>=0xDC00 and low<=0xDFFF,'Invalid surrogate');code=0x10000+(code-0xD800)*1024+low-0xDC00;pos=pos+6
     else assert(code<0xDC00 or code>0xDFFF,'Invalid surrogate')end
     out[#out+1]=unicode.char(code)
    else assert(escapes[e],'Invalid JSON escape');out[#out+1]=escapes[e]end
   else assert(ch:byte()>=32,'Control character in JSON');out[#out+1]=ch end
  end
  error('Unterminated JSON string')
 end
 value=function(depth)
  nodes=nodes+1;assert(depth<=8 and nodes<=512,'JSON too complex');ws();local ch=text:sub(pos,pos)
  if ch=='"'then return str()end
  if ch=='{'or ch=='['then
   local object=ch=='{';local result={};pos=pos+1;ws();local ending=object and '}'or ']'
   if text:sub(pos,pos)==ending then pos=pos+1;return result end
   while true do
    ws();local key=object and str()or #result+1
    if object then ws();assert(text:sub(pos,pos)==':','Expected colon');pos=pos+1;assert(result[key]==nil,'Duplicate JSON key')end
    result[key]=value(depth+1);ws();local delimiter=text:sub(pos,pos);pos=pos+1
    if delimiter==ending then return result end;assert(delimiter==',','Expected comma')
   end
  end
  for _,row in ipairs({{'true',true},{'false',false},{'null',M.null}})do if text:sub(pos,pos+#row[1]-1)==row[1]then pos=pos+#row[1];return row[2]end end
  local token=text:match('^-?%d+%.?%d*[eE]?[+-]?%d*',pos);assert(token,'Invalid JSON value')
  assert(not token:match('^-?0%d')and not token:match('%.$')and(not token:find('.',1,true)or token:match('%.%d')),'Invalid JSON number');local number=tonumber(token);assert(number and number==number and math.abs(number)<math.huge,'Invalid JSON number');pos=pos+#token;return number
 end
 local result=value(0);ws();assert(pos>#text,'Trailing JSON content');return result
end
local function read(path,limit)
 local f=assert(io.open(path,'r'),'Missing page file: '..path);local text=f:read(limit+1)or '';f:close();assert(#text<=limit,'Page package too large');return text
end
function M.package(directory)
 local manifest=M.decode(read(fs.concat(directory,'app.json'),16384))
 assert(type(manifest)=='table'and type(manifest.files)=='table'and #manifest.files>=1 and #manifest.files<=32,'Invalid app.json')
 local files,total={},0
 for _,name in ipairs(manifest.files)do
  assert(type(name)=='string'and #name<=100 and name:match('^[%w_./-]+$')and name:sub(1,1)~='/'and not name:find('..',1,true)and not name:find('//',1,true),'Invalid page path')
  assert(not files[name],'Duplicate page file');files[name]=read(fs.concat(directory,name),262144-total);total=total+#files[name]
 end
 return {id=manifest.id,title=manifest.title,entry=manifest.entry or 'index.html',events=manifest.events or {},files=files}
end
return M
]=]},
{path=[=[/home/ochd_web_demo.lua]=],text=[=[-- Demonstrates real OC metrics and round-trip UI events. Never controls machines.
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
]=]},
{path=[=[/home/ochd_apps/demo/app.json]=],text=[=[{"id":"ochd-demo","title":"OCHD · 自定义网页看板","entry":"index.html","files":["index.html","style.css","main.js"],"events":["set_target","reset_counter"]}
]=]},
{path=[=[/home/ochd_apps/demo/index.html]=],text=[=[<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>OCHD 自定义看板</title><link rel="stylesheet" href="style.css"></head>
<body><header><strong>OCHD<span>网页应用</span></strong><nav>运行总览 <b>自定义看板</b> 页面开发</nav><small id="connection">等待 OC 数据</small></header>
<main><section class="heading"><div><p>YOUR PAGE · YOUR DATA</p><h1>我的 OC 看板</h1><span>HTML / CSS / JavaScript 页面包，数据由 Lua 提供。</span></div><div class="badge">WebKit · 1080p</div></section>
<section class="metrics"><article><label>OC 运行时间</label><h2 id="uptime">—</h2><small>真实 OC computer.uptime()</small></article><article class="teal"><label>可用内存</label><h2 id="memory">—</h2><small id="memory-detail">等待电脑数据</small></article><article class="gold"><label>看板目标</label><h2 id="target">—</h2><small>通过网页请求 Lua 修改</small></article></section>
<section class="grid"><article class="chart"><h3>数据变化</h3><p>最近的可用内存快照，曲线在客户端绘制。</p><canvas id="history" width="1100" height="350"></canvas><div class="legend"><i></i> OC 可用内存 <span>每秒推送一次 · 不扫描机器</span></div></article><article class="form"><h3>网页 → OC → 回执</h3><p>这个示例只修改自己的演示参数，不控制机器。</p><label for="new-target">新的目标值</label><input id="new-target" type="number" min="1" max="1000000" placeholder="支持 Ctrl+V 粘贴"><button id="save">保存目标</button><button class="secondary" id="reset">清零演示计数</button><div id="notice">等待第一次数据推送</div><small>收到回执后更新显示，页面刷新不会替你重复执行操作。</small></article></section>
<footer>编辑自己的页面包即可改布局、图表和表单，无需重新编译模组。<span id="counter">已推送 0 次</span></footer></main><script src="main.js"></script></body></html>
]=]},
{path=[=[/home/ochd_apps/demo/style.css]=],text=[=[*{box-sizing:border-box}body{margin:0;background:#eef1f8;color:#203252;font:16px "Microsoft YaHei",sans-serif}header{height:72px;background:#fff;display:flex;align-items:center;padding:0 40px;gap:70px}header strong{font-size:25px;letter-spacing:1px}header strong span{font-size:16px;font-weight:400;margin-left:16px;letter-spacing:0}nav{color:#8290a7;word-spacing:30px;font-size:15px}nav b{color:#6688ff;font-weight:500}header small{margin-left:auto;color:#8290a7}main{padding:25px 40px}.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.heading p{margin:0 0 6px;font-size:11px;letter-spacing:2px;color:#8290a7}h1{margin:0 0 8px;font-size:29px;font-weight:500}.heading span,p{color:#8290a7;font-size:13px}.badge{background:#e1e8ff;border-radius:20px;color:#6688ff;padding:10px 20px;font-size:13px}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-bottom:23px}article{background:white;border-radius:14px;box-shadow:0 4px 0 #dfe4ee}.metrics article{padding:20px 24px;border-left:5px solid #6688ff}.metrics label{color:#8290a7;font-size:14px}h2{font-size:29px;margin:10px 0;color:#203252;font-weight:500}small{font-size:11px;color:#8290a7}.metrics .teal{border-color:#22bdb8}.metrics .gold{border-color:#e7af3f}.grid{display:grid;grid-template-columns:1.6fr 1fr;gap:22px}.grid article{padding:22px 24px;min-height:313px}h3{font-size:17px;color:#6688ff;font-weight:500;margin:0 0 12px}p{line-height:1.6;margin:8px 0 17px}canvas{width:100%;height:168px;margin-top:6px}.legend{font-size:11px;color:#8290a7;display:flex;align-items:center;gap:8px}.legend i{width:7px;height:7px;background:#22bdb8;border-radius:50%}.legend span{margin-left:auto}.form label{font-size:12px;color:#8290a7;display:block;margin-bottom:6px}input{border:1px solid #dfe5f1;border-radius:7px;background:#f5f7fc;padding:10px 12px;font:14px "Microsoft YaHei",sans-serif;color:#203252;width:100%;outline:none}input:focus{border-color:#6688ff}button{border:0;border-radius:8px;background:#6688ff;color:white;padding:11px 16px;font:14px "Microsoft YaHei",sans-serif;cursor:pointer;margin:12px 8px 0 0}.secondary{background:#e4ebff;color:#6688ff}button:disabled{opacity:.5;cursor:wait}#notice{font-size:12px;color:#22a89f;min-height:32px;margin-top:14px}.form small{line-height:1.5;display:block}footer{font-size:11px;color:#8290a7;display:flex;justify-content:space-between;padding-top:24px}
]=]},
{path=[=[/home/ochd_apps/demo/main.js]=],text=[=[(function(){
  const $=id=>document.getElementById(id), history=[];
  function paint(){const c=$('history'),g=c.getContext('2d');g.clearRect(0,0,c.width,c.height);g.strokeStyle='#edf0f7';g.lineWidth=2;for(let i=1;i<4;i++){g.beginPath();g.moveTo(0,i*85);g.lineTo(c.width,i*85);g.stroke();}if(history.length<2)return;g.strokeStyle='#22bdb8';g.lineWidth=5;g.beginPath();history.forEach((v,i)=>{let x=i/(history.length-1)*c.width,y=320-v*270;i?g.lineTo(x,y):g.moveTo(x,y);});g.stroke();}
  OC.onData(data=>{ $('connection').textContent=data._online?'OC 数据已连接':'OC 离线 · 显示最后快照';$('uptime').textContent=Math.floor(data.uptime||0)+' 秒';$('memory').textContent=((data.freeMemory||0)/1024).toFixed(1)+' KiB';$('memory-detail').textContent='总内存 '+((data.totalMemory||0)/1024).toFixed(1)+' KiB';$('target').textContent=Number(data.target||0).toLocaleString();$('counter').textContent='已推送 '+(data.counter||0)+' 次';if(document.activeElement!==$('new-target'))$('new-target').value=data.target||144000;history.push((data.freeMemory||0)/Math.max(1,data.totalMemory||1));if(history.length>40)history.shift();paint();if($('notice').textContent==='等待第一次数据推送')$('notice').textContent='页面已连接，可以测试按钮和粘贴。';});
  async function send(name,args){$('save').disabled=$('reset').disabled=true;$('notice').textContent='等待 OC 回执…';try{const result=await OC.emit(name,args);$('notice').textContent=result.message;}catch(error){$('notice').textContent=error.message||'操作未确认，请核对 OC';}finally{$('save').disabled=$('reset').disabled=false;}}
  $('save').onclick=()=>send('set_target',{target:Number($('new-target').value)});$('reset').onclick=()=>send('reset_counter',{});
})();
]=]}}

local fs=require('filesystem');local originals={};local same=true
local function read(p)local f=io.open(p,'r');if not f then return end;local s=f:read('*a');f:close();return s end
for i,row in ipairs(files)do
 if row.path:sub(-4)=='.lua'then assert(load(row.text,'@'..row.path,'t',_ENV),'Lua syntax check failed')end
 originals[i]=read(row.path)or false;if originals[i]~=row.text then same=false end
end
if same then print('HTML demo already installed');return end
local n=1;while fs.exists(files[1].path..'.before-web-'..n)or fs.exists(files[1].path..'.web-new-'..n)do n=n+1 end
for _,row in ipairs(files)do
 assert(not fs.exists(row.path..'.before-web-'..n)and not fs.exists(row.path..'.web-new-'..n),'Backup collision')
 local dir=fs.path(row.path);assert(fs.exists(dir)or fs.makeDirectory(dir),'Create directory failed')
 local f=assert(io.open(row.path..'.web-new-'..n,'w'));assert(f:write(row.text));assert(f:flush());f:close();assert(read(row.path..'.web-new-'..n)==row.text,'Read-back failed')
end
local retired,installed={},{}
local ok,reason=pcall(function()
 for i,row in ipairs(files)do
  assert((read(row.path)or false)==originals[i],'Source changed')
  if originals[i]then assert(fs.rename(row.path,row.path..'.before-web-'..n));retired[i]=true end
  assert(fs.rename(row.path..'.web-new-'..n,row.path));installed[i]=true;assert(read(row.path)==row.text)
 end
end)
if not ok then
 for i=#files,1,-1 do local p=files[i].path;if installed[i]then assert(fs.rename(p,p..'.web-failed-'..n))end;if retired[i]then assert(fs.rename(p..'.before-web-'..n,p))end end
 error('Install failed, originals restored: '..tostring(reason))
end
print('HTML demo installed; no machine control or automatic startup.')
print('lua /home/ochd_web_demo.lua')
