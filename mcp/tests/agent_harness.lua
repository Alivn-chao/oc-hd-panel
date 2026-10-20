local files = { ["/log"]="hello\n\"quoted\"\\line\000", ["/program.lua"]="old program" }
local dirs = {["/"]=true}
local jobs, results = {}, {}
local now, threads = 0, {}
local modules = {}
function require(name) return assert(modules[name],name) end
local fakefs = {}
function fakefs.exists(p) return files[p]~=nil or dirs[p]~=nil end
function fakefs.isDirectory(p) return dirs[p] or false end
function fakefs.size(p) return files[p] and #files[p] or 0 end
function fakefs.canonical(p) return p:gsub("/+","/") end
function fakefs.makeDirectory(p) dirs[p]=true;return true end
function fakefs.remove(p) files[p]=nil;return true end
function fakefs.rename(a,b) if not files[a] then return nil,"missing" end;files[b]=files[a];files[a]=nil;return true end
function fakefs.list(p)
  local keys={};for k in pairs(files)do keys[#keys+1]=k:sub(2)end;table.sort(keys);local n=0
  return function()n=n+1;return keys[n]end
end
function fakefs.mounts() local done=false;return function()if not done then done=true;return {address="disk"},"/" end end end
modules.filesystem=fakefs
io={}
function io.open(p,mode)
 if mode=="rb" and not files[p] then return nil,"not found" end
 if mode=="wb" then files[p]="" end
 local f={pos=mode=="ab" and #(files[p] or "") or 0}
 function f:read(n)local s=files[p]:sub(self.pos+1,self.pos+n);self.pos=self.pos+#s;if #s>0 then return s end end
 function f:write(s)files[p]=files[p]:sub(1,self.pos)..s..files[p]:sub(self.pos+#s+1);self.pos=self.pos+#s;return true end
 function f:close()return true end
 function f:seek(_,n)self.pos=n;return n end
 return f
end
local stdout,stderr
function io.output(v)stdout=v end
function io.error(v)stderr=v end
function io.input(v)end
modules.buffer={new=function(mode,stream)stream.setvbuf=function()end;return stream end}
modules.shell={setWorkingDirectory=function()return true end,execute=function(cmd)
  if cmd=="fail" then error("failure") end
  if cmd=="wait" then while true do coroutine.yield()end end
  stdout:write(cmd=="large" and string.rep("x",20000) or "hello from shell\n")
  stderr:write("stderr\n");return true
end}
modules.computer={uptime=function()return now end,freeMemory=function()return 500 end,totalMemory=function()return 1000 end,energy=function()return 10 end,maxEnergy=function()return 20 end}
modules.process={list={ {command="sh",path="/bin/sh"} }}
modules.event={listen=function(_,fn) _init=fn end}
local card={next=function()local j=table.remove(jobs,1);if j then return j.id,j.op,j.args end end,complete=function(id,result)results[id]=result;return true end}
modules.component={list=function()local done=false;return function()if not done then done=true;return "case" end end end,proxy=function()return card end,invoke=function(address,method,...)return method,...end}
modules.thread={create=function(fn)
 local t={co=coroutine.create(fn),killed=false}
 function t:detach()return self end
 function t:status()return self.killed and "dead" or coroutine.status(self.co)=="dead" and "dead" or "running" end
 function t:kill()self.killed=true end
 threads[#threads+1]=t
 local ok,err=coroutine.resume(t.co);assert(ok,err);return t
end}
os.sleep=function()coroutine.yield()end
function step()
 now=now+0.5
 for _,t in ipairs(threads)do if t:status()~="dead"then local ok,err=coroutine.resume(t.co);assert(ok,err)end end
end
function submit(id,op,args)jobs[#jobs+1]={id=id,op=op,args=args or {}};for i=1,4 do step()end;return results[id]end
function result(id)return results[id]end
function getFile(p)return files[p]end
function putFile(p,s)files[p]=s end
