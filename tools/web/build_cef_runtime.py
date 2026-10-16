"""Build the pinned Windows x64 Chromium/CEF runtime without downloading at launch.

Set OCHD_BUILD_JAVA_HOME to a JDK 21+ directory. All binary dependencies are
SHA-256 checked; upstream native licenses are retained inside the package.
"""
import hashlib
import io
import json
import os
import subprocess
import tarfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def build():
    deps = json.loads((ROOT/'web-runtime/cef/dependencies.json').read_text())
    cache = ROOT/'build/cef'
    cache.mkdir(parents=True, exist_ok=True)
    for name, item in deps.items():
        target = cache/name
        if not target.exists():
            with urllib.request.urlopen(item['source'], timeout=180) as response:
                target.write_bytes(response.read())
        assert hashlib.sha256(target.read_bytes()).hexdigest() == item['sha256'], name+' hash mismatch'
    home = Path(os.environ['OCHD_BUILD_JAVA_HOME'])
    suffix = '.exe' if os.name == 'nt' else ''
    classes = ROOT/'build/cef-host'
    classes.mkdir(parents=True, exist_ok=True)
    sources = list((ROOT/'web-runtime/cef').glob('*.java'))
    sources += [ROOT/'src/main/java/dev/bec/hdpanel'/name for name in ['WebMappedFrames.java', 'WebFrameCodec.java']]
    cp = os.pathsep.join(str(cache/name) for name in ['jcef-api.jar', 'jcefmaven.jar', 'gson.jar'])
    subprocess.run([str(home/('bin/javac'+suffix)), '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, sources)], check=True)
    host = ROOT/'build/cef-host.jar'
    subprocess.run([str(home/('bin/jar'+suffix)), '--create', '--file', str(host), '-C', str(classes), '.'], check=True)
    out = ROOT/'build/distributions/ochd-web-runtime-win-x64.zip'
    out.parent.mkdir(parents=True, exist_ok=True)
    prefix = 'ochd-web-runtime/'
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(host, prefix+'cef-host.jar')
        for name in deps:
            if name != 'jcef-natives.jar':
                z.write(cache/name, prefix+'cef-lib/'+name)
        with zipfile.ZipFile(cache/'jcef-natives.jar') as natives:
            archive = next(n for n in natives.namelist() if n.endswith('.tar.gz'))
            with tarfile.open(fileobj=io.BytesIO(natives.read(archive)), mode='r:gz') as tar:
                for member in tar:
                    if not member.isfile():
                        continue
                    name = Path(member.name).as_posix()
                    assert not name.startswith('/') and '..' not in name.split('/')
                    z.writestr(prefix+'native/'+name, tar.extractfile(member).read())
        z.write(ROOT/'web-runtime/cef/dependencies.json', prefix+'cef-dependencies.json')
        for source in sources:
            z.write(source, prefix+'source/'+source.name)
        for name in ['CHROMIUM.md', 'DISPLAY_POWER.md']:
            z.write(ROOT/'docs'/name, prefix+name)
        # Maven archives contain their upstream Apache notices.
        for name in deps:
            if name == 'jcef-natives.jar':
                continue
            with zipfile.ZipFile(cache/name) as jar:
                for entry in jar.namelist():
                    if not entry.endswith('/') and any(word in entry.upper() for word in ['LICENSE', 'NOTICE', 'COPYING']):
                        z.writestr(prefix+'legal/'+name+'/'+entry, jar.read(entry))
    print('Built', out, out.stat().st_size, 'bytes')


if __name__ == '__main__':
    build()
