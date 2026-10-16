"""Small client installer; fetch the pinned public CEF archive during installation.
The complete offline client ZIP remains the preferred local/USB distribution.
"""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/distributions'
client=OUT/'ochd-html-client-win-x64.zip'
target=OUT/'ochd-chromium-client-setup-win-x64.zip'
manifest={'client':{},'native':{}}
with zipfile.ZipFile(client) as source,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as dest:
    for name in source.namelist():
        data=source.read(name)
        if name.startswith('ochd-web-runtime/native/'):
            manifest['native'][name.split('/native/',1)[1]]=hashlib.sha256(data).hexdigest()
        else:
            dest.writestr(name,data)
            manifest['client'][name]=hashlib.sha256(data).hexdigest()
    dest.write(ROOT/'LICENSE','OCHD-LICENSE.txt')
    manifest['client']['OCHD-LICENSE.txt']=hashlib.sha256((ROOT/'LICENSE').read_bytes()).hexdigest()
    dest.writestr('ochd-web-runtime/setup-files.json',json.dumps(manifest,indent=2))
    dest.write(ROOT/'tools/web/install_chromium.ps1','install-chromium.ps1')
    dest.writestr('install-chromium.bat','@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-chromium.ps1"\r\nif errorlevel 1 (echo Installation failed. Keep this message for diagnosis.)\r\npause\r\n')
    dest.writestr('INSTALL-FIRST.txt', '''OCHD alpha8 Chromium client (Windows x64, Java 21/25)

1. Close Minecraft. Extract this ZIP into the game directory containing options.txt and mods/.
2. Double-click install-chromium.bat. It downloads the pinned 162 MB official CEF bundle
   from repo.maven.apache.org, verifies SHA256 and all extracted files, then installs it.
   Internet is needed during installation, not at game launch. No administrator access needed.
3. After SUCCESS, start Minecraft. Old OCHD JARs are moved into runtime/old-mods-* backups.
4. Server: also replace its OCHD JAR with alpha8 and restart. Do not install Chromium on servers.

If this ZIP is kept in Downloads instead of the game directory, installation refuses to run.
Existing BEC pages and Lua files do not need reinstalling.
The 165 MB complete offline client ZIP can be used instead; it needs no installation download.
''')
sha=hashlib.sha256(target.read_bytes()).hexdigest()
assets=[OUT/'oc-hd-panel-0.4.0-alpha8-chromium.jar',target,OUT/'ochd-html-demo.zip',OUT/'alpha4-preservation.json']
(OUT/'CHROMIUM-SETUP-SHA256.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in assets))
print(target.name,target.stat().st_size,sha)
