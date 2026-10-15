from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def literal(s):
    for n in range(1,20):
        ending=']'+'='*n+']'
        if ending not in s:return '['+'='*n+'['+s+ending
    raise ValueError('delimiter collision')
files=[('/home/ochd_web.lua',ROOT/'oc/ochd_web.lua'),('/home/ochd_web_demo.lua',ROOT/'oc/web_demo.lua')]
files += [('/home/ochd_apps/demo/'+name,ROOT/'web-demo'/name)for name in ['app.json','index.html','style.css','main.js']]
entries=['{path='+literal(path)+',text='+literal(source.read_text(encoding='utf-8'))+'}'for path,source in files]
body=r'''
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
'''
(ROOT/'oc/install_web_demo.lua').write_text('local files={'+',\n'.join(entries)+'}\n'+body,encoding='utf-8',newline='\n')
