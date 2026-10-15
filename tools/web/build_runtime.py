"""Build the isolated WebKit host and a Windows x64 client runtime ZIP."""
import hashlib
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
LIB=ROOT/'web-runtime/lib'
def download(url,path,digest=None):
    if not path.exists()or(digest and hashlib.sha256(path.read_bytes()).hexdigest()!=digest):
        with urllib.request.urlopen(url,timeout=60)as response:data=response.read()
        if digest:assert hashlib.sha256(data).hexdigest()==digest,'Dependency hash mismatch'
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)

def build():
    LIB.mkdir(parents=True,exist_ok=True)
    deps=json.loads((ROOT/'web-runtime/dependencies.json').read_text())
    for name,item in deps.items():download(item['source'],LIB/name,item['sha256'])
    home=Path(os.environ.get('OCHD_BUILD_JAVA_HOME','C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot'))
    suffix='.exe'if os.name=='nt'else ''
    target=ROOT/'build/web-host';target.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(home/('bin/javac'+suffix)),'--module-path',str(LIB),'--add-modules','javafx.web,javafx.swing','-cp',str(LIB/'gson.jar'),'-d',str(target),str(ROOT/'web-runtime/src/dev/bec/hdweb/Host.java'),str(ROOT/'src/main/java/dev/bec/hdpanel/WebCommandQueue.java'),str(ROOT/'src/main/java/dev/bec/hdpanel/WebFrameCodec.java')],check=True)
    jar=ROOT/'build/web-host.jar'
    subprocess.run([str(home/('bin/jar'+suffix)),'--create','--file',str(jar),'-C',str(target),'.'],check=True)
    legal=json.loads((ROOT/'web-runtime/legal-source.json').read_text())
    sdk=ROOT/'build-tools/javafx-sdk-21.0.12.zip';download(legal['url'],sdk,legal['sha256'])
    apache=ROOT/'build/web-apache-license.txt';download('https://www.apache.org/licenses/LICENSE-2.0.txt',apache)
    out=ROOT/'build/distributions/ochd-web-runtime-win-x64.zip';out.parent.mkdir(parents=True,exist_ok=True)
    prefix='ochd-web-runtime/'
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED)as z:
        z.write(jar,prefix+'host.jar')
        for name in deps:z.write(LIB/name,prefix+'lib/'+name)
        for name in ['dependencies.json','legal-source.json']:z.write(ROOT/'web-runtime'/name,prefix+name)
        z.write(ROOT/'web-runtime/src/dev/bec/hdweb/Host.java',prefix+'source/dev/bec/hdweb/Host.java')
        z.write(ROOT/'src/main/java/dev/bec/hdpanel/WebCommandQueue.java',prefix+'source/dev/bec/hdpanel/WebCommandQueue.java')
        z.write(ROOT/'src/main/java/dev/bec/hdpanel/WebFrameCodec.java',prefix+'source/dev/bec/hdpanel/WebFrameCodec.java')
        z.write(ROOT/'docs/HTML_APPS.md',prefix+'README.md')
        z.write(apache,prefix+'legal/gson/LICENSE-2.0.txt')
        z.writestr(prefix+'legal/THIRD_PARTY.txt','OpenJFX 21.0.12 (GPLv2 with Classpath exception and component notices), unmodified official binaries. Source: https://github.com/openjdk/jfx21u ; matching release sources: https://github.com/openjdk/jfx21u/tags\nGson 2.2.4 (Apache-2.0), unmodified Maven binary. Source: https://github.com/google/gson/tree/gson-parent-2.2.4\nHost source is included under source/. Runtime can be rebuilt using the OCHD repository tools/web/build_runtime.py and pinned dependency manifest.\n')
        with zipfile.ZipFile(sdk)as source:
            for name in source.namelist():
                if '/legal/'in name and not name.endswith('/'):
                    relative=name.split('/legal/',1)[1];assert '..'not in relative.split('/');z.writestr(prefix+'legal/openjfx/'+relative,source.read(name))
    print('Built',out.name,out.stat().st_size,'bytes')

if __name__=='__main__':build()
