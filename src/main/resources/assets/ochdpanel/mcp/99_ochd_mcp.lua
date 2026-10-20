-- OCHD MCP OpenOS agent. Installed by the authenticated bridge while the host is stopped.
-- No ports are opened by this script. Requests come from the linked OCHD case.
local event = require("event")
event.listen("init", function()
  local component, computer = require("component"), require("computer")
  local thread, fs, process = require("thread"), require("filesystem"), require("process")
  local shell = require("shell")
  local uploads, running = {}, {}
  local MAX_FILE, MAX_CHUNK, MAX_OUTPUT = 1048576, 8192, 16384
  local function quote(s)
    return '"' .. tostring(s):gsub('[%z\1-\31\\"]', function(c)
      if c == '"' then return '\\"' elseif c == '\\' then return '\\\\' end
      return string.format("\\u%04x", c:byte())
    end) .. '"'
  end
  local function json(v, depth, seen)
    depth, seen = depth or 0, seen or {}
    if type(v) == "nil" then return "null"
    elseif type(v) == "boolean" then return tostring(v)
    elseif type(v) == "number" then return v == v and math.abs(v) ~= math.huge and tostring(v) or "null"
    elseif type(v) == "string" then return quote(v:sub(1, 32768))
    elseif type(v) ~= "table" or depth > 6 or seen[v] then return quote(tostring(v)) end
    seen[v] = true
    local out, n = {}, 0
    for k, value in pairs(v) do
      n = n + 1; if n > 128 then break end
      out[#out+1] = quote(k) .. ":" .. json(value, depth+1, seen)
    end
    seen[v] = nil
    return "{" .. table.concat(out, ",") .. "}"
  end
  local function finish(card, id, ok, result)
    local encoded = json(ok and {ok=true, value=result} or {ok=false, error=tostring(result)})
    if #encoded > 60000 then encoded = '{"ok":false,"error":"response too large; request a smaller page"}' end
    pcall(card.complete, id, encoded)
  end
  local function path(s)
    assert(type(s)=="string" and #s<=512 and not s:find("\0",1,true) and not s:find("\\",1,true), "invalid OC path")
    for part in s:gmatch("[^/]+") do assert(part~="..", "parent traversal forbidden") end
    return "/" .. fs.canonical(s):gsub("^/+", "")
  end
  local function readAll(p)
    if not fs.exists(p) then return nil end
    assert(not fs.isDirectory(p) and fs.size(p)<=MAX_FILE, "file exceeds 1 MiB editing limit")
    local f=assert(io.open(p,"rb"));local value=f:read(MAX_FILE+1) or "";f:close()
    assert(#value<=MAX_FILE,"file exceeds editing limit");return value
  end
  local function version(data)
    if data==nil then return "missing" end
    local a,b=1,0
    for i=1,#data do a=(a+data:byte(i))%65521;b=(b+a)%65521 end
    return string.format("%08x:%d",b*65536+a,#data)
  end
  local function hex(s) return (s:gsub(".",function(c)return string.format("%02x",c:byte())end)) end
  local function unhex(s)
    assert(type(s)=="string" and #s<=MAX_CHUNK*2 and #s%2==0 and not s:find("[^0-9a-fA-F]"),"invalid hex chunk")
    return (s:gsub("..",function(c)return string.char(tonumber(c,16))end))
  end
  local function action(op,a,id,cardAddress)
    if op=="runtime" then
      local procs,mounts={},{};local n=0
      for _,p in pairs(process.list) do n=n+1;if n>64 then break end;procs[n]={command=tostring(p.command):sub(1,200),path=tostring(p.path):sub(1,200)} end
      n=0;for proxy,mount in fs.mounts() do n=n+1;if n>64 then break end;mounts[n]={address=proxy.address,path=mount} end
      return {freeMemory=computer.freeMemory(),totalMemory=computer.totalMemory(),energy=computer.energy(),maxEnergy=computer.maxEnergy(),uptime=computer.uptime(),processes=procs,mounts=mounts}
    elseif op=="files" then
      local p=path(a.path or "/");local out={};local skip=math.max(0,math.floor(a.offset or 0));local n=0
      assert(skip<=4096,"directory offset too large")
      for name in assert(fs.list(p)) do
        n=n+1;if n>skip then out[#out+1]=name end;if #out>=64 then break end
      end
      return {entries=out,nextOffset=skip+#out,mayHaveMore=#out==64}
    elseif op=="read" then
      local p=path(a.path);assert(not fs.isDirectory(p),"not a file")
      local size=fs.size(p);local offset=math.floor(a.offset or 0);local count=math.floor(a.count or MAX_CHUNK)
      assert(offset>=0 and offset<=size and count>=1 and count<=MAX_CHUNK,"invalid read range")
      local f=assert(io.open(p,"rb"));assert(f:seek("set",offset));local data=f:read(count) or "";f:close()
      return {hex=hex(data),offset=offset,size=size,eof=offset+#data>=size,version=offset==0 and (size<=MAX_FILE and version(readAll(p)) or "too-large-to-edit") or nil}
    elseif op=="write" then
      local key=cardAddress..":"..tostring(a.upload or id)
      if a.phase=="begin" then
        local count=0;for _ in pairs(uploads) do count=count+1 end;assert(count<4,"upload queue full")
        local p=path(a.path);assert(a.expected==version(readAll(p)),"file changed; read it again before editing")
        local staging=(p:match("^(.*)/[^/]*$") or "").."/.ochd-mcp"
        assert(fs.makeDirectory(staging) or fs.isDirectory(staging),"cannot create staging directory")
        local temp=staging.."/"..id..".tmp";assert(not fs.exists(temp),"staging file exists")
        local f=assert(io.open(temp,"wb"));f:close()
        uploads[key]={path=p,temp=temp,backup=staging.."/"..id..".bak",expected=a.expected,size=0,time=computer.uptime()}
        return {upload=id}
      end
      local u=assert(uploads[key],"upload unknown or expired")
      if a.phase=="chunk" then
        local data=unhex(a.hex);assert(a.offset==u.size and u.size+#data<=MAX_FILE,"invalid upload offset or size")
        local f=assert(io.open(u.temp,"ab"));local ok,why=f:write(data);f:close();assert(ok,why)
        u.size=u.size+#data;u.time=computer.uptime();return {size=u.size}
      elseif a.phase=="commit" then
        assert(u.expected==version(readAll(u.path)),"file changed during upload")
        local backup=u.backup;local exists=fs.exists(u.path)
        assert(not fs.exists(backup),"backup exists")
        if exists then assert(fs.rename(u.path,backup)) end
        local ok,why=fs.rename(u.temp,u.path)
        if not ok then if exists then fs.rename(backup,u.path) end;error(why or "commit failed") end
        uploads[key]=nil;return {written=u.size,backup=exists and backup or nil}
      elseif a.phase=="abort" then fs.remove(u.temp);uploads[key]=nil;return {aborted=true} end
      error("invalid upload phase")
    elseif op=="invoke" then
      assert(type(a.address)=="string" and type(a.method)=="string","address and method required")
      assert(a.method~="next" and a.method~="complete","agent methods cannot be invoked remotely")
      local args=a.arguments or {};local list={};for i=1,32 do if args[tostring(i)]~=nil then list[i]=args[tostring(i)] elseif args[i]~=nil then list[i]=args[i] else break end end
      return table.pack(component.invoke(a.address,a.method,table.unpack(list)))
    end
    error("unknown agent operation")
  end
  local function startCommand(card,id,a)
    assert(type(a.command)=="string" and #a.command<=4096,"command too long")
    local count=0;for _ in pairs(running) do count=count+1 end;assert(count<2,"two commands already running")
    local entry={card=card,id=id,output="",truncated=false,deadline=computer.uptime()+math.max(1,math.min(120,tonumber(a.timeout) or 30))}
    running[id]=entry
    entry.worker=thread.create(function()
      local buffer=require("buffer")
      local stream={write=function(_,s)
        local room=MAX_OUTPUT-#entry.output;entry.output=entry.output..s:sub(1,math.max(0,room));if #s>room then entry.truncated=true end;return true
      end,close=function()return true end,read=function()return nil end}
      local output=buffer.new("w",stream);output:setvbuf("no")
      io.output(output);io.error(output);io.input(buffer.new("r",stream))
      if a.cwd then assert(shell.setWorkingDirectory(path(a.cwd))) end
      local ok,success,why=pcall(shell.execute,a.command)
      entry.result={success=ok and success~=false and success~=nil,detail=tostring(why or (not ok and success) or ""),output=entry.output,truncated=entry.truncated}
      entry.done=true
    end):detach()
  end
  thread.create(function()
    while true do
      for id,e in pairs(running) do
        if e.done then finish(e.card,id,true,e.result);running[id]=nil
        elseif computer.uptime()>e.deadline or e.worker:status()=="dead" then
          e.worker:kill();finish(e.card,id,false,"command timed out or worker failed; partial output: "..e.output);running[id]=nil
        end
      end
      for key,u in pairs(uploads) do if computer.uptime()-u.time>120 then fs.remove(u.temp);uploads[key]=nil end end
      local scanned=0
      for address in component.list("ochd_mcp",true) do
        scanned=scanned+1;if scanned>8 then break end
        local card=component.proxy(address)
        local ok,id,op,args=pcall(card.next)
        if ok and id then
          if op=="run" then local started,why=pcall(startCommand,card,id,args or {});if not started then finish(card,id,false,why) end
          else local success,result=pcall(action,op,args or {},id,address);finish(card,id,success,result) end
        end
      end
      os.sleep(0.5)
    end
  end):detach()
  return false
end)
