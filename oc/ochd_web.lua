-- HTML package loader and bounded JSON decoder. Never evaluates page-provided Lua.
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
