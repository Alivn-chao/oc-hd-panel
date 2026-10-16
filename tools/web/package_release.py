from pathlib import Path
import hashlib
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/distributions'
MOD=OUT/'oc-hd-panel-0.4.0-alpha8-chromium.jar'
assert MOD.exists()
DEMO=OUT/'ochd-html-demo.zip'
with zipfile.ZipFile(DEMO,'w',zipfile.ZIP_DEFLATED)as z:
    for name in ['ochd_web.lua','web_demo.lua','install_web_demo.lua']:z.write(ROOT/'oc'/name,'oc/'+name)
    for p in (ROOT/'web-demo').iterdir():z.write(p,'apps/demo/'+p.name)
    z.write(ROOT/'docs/HTML_APPS.md','README.md')
CLIENT=OUT/'ochd-html-client-win-x64.zip'
with zipfile.ZipFile(CLIENT,'w',zipfile.ZIP_DEFLATED)as z:
    z.write(MOD,'mods/'+MOD.name)
    with zipfile.ZipFile(OUT/'ochd-web-runtime-win-x64.zip')as runtime:
        for name in runtime.namelist():z.writestr(name,runtime.read(name))
    z.write(ROOT/'docs/HTML_APPS.md','OCHD-HTML-README.md')
    z.write(ROOT/'docs/DISPLAY_POWER.md','OCHD-DISPLAY-POWER.md')
    z.write(ROOT/'oc/install_web_demo.lua','oc/install_web_demo.lua')
assets=[MOD,CLIENT,DEMO,OUT/'alpha4-preservation.json']
(OUT/'HTML-SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'for p in assets),encoding='utf-8')
for p in assets:print(p.name,p.stat().st_size)
