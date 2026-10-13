from pathlib import Path
import struct, zlib, zipfile, json

root=Path(__file__).resolve().parents[1]
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
raw=bytearray()
for y in range(16):
    raw.append(0)
    for x in range(16):
        color=(39,47,64,255)
        if x in (0,15) or y in (0,15): color=(70,82,106,255)
        if y==14 and 11<=x<=13: color=(34,189,184,255)
        raw.extend(color)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
path=root/'src/main/resources/assets/ochdpanel/textures/blocks/panel.png'
path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(png)
info=[dict(modid='ochdpanel',name='OC HD Panel',description='Reusable 720p OpenComputers screens with declarative Lua UI, screen walls and an optional BEC adapter.',
           version='0.3.0-alpha3',mcversion='1.7.10',authorList=['OC HD Panel contributors'],dependencies=['OpenComputers'])]
(root/'src/main/resources/mcmod.info').write_text(json.dumps(info,indent=2),encoding='utf-8')
with zipfile.ZipFile(root/'libs/OpenComputers-1.12.61-GTNH.jar') as archive:
    print(archive.read('mcmod.info').decode('utf-8')[:1200])
