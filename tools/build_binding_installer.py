from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def literal(s):
    for n in range(1,10):
        if ']'+'='*n+']'not in s:return '['+'='*n+'['+s+']'+'='*n+']'
    raise ValueError('delimiter')
files=['ochd_bindings.lua','bec_v2_setup_hd.lua','bec_hd.lua']
entries=','.join('{path="/home/'+name+'",source='+literal((ROOT/'oc'/name).read_text(encoding='utf-8'))+'}'for name in files)
body='local files={'+entries+'}\n'+r'''
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
print('Requires matching 0.3.1-alpha4-bindings mod on BOTH client and server.')
print('V2 setup page: lua /home/bec_v2_setup_hd.lua')
print('V1 monitoring bindings: lua /home/bec_hd.lua monitor')
'''
(ROOT/'oc/install_bindings.lua').write_text(body,encoding='utf-8',newline='\n')
