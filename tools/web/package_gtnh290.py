"""Package the verified mod and offline Windows x64 Chromium runtime for a fresh instance."""
import hashlib
import json
import re
import shutil
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/distributions/gtnh290'
VERSION = re.search(r"^version = '([^']+)'", (ROOT / 'build.gradle').read_text(), re.M)[1]
MOD = ROOT / 'build/libs' / ('oc-hd-panel-' + VERSION + '.jar')
RUNTIME = ROOT / 'build/distributions/ochd-web-runtime-win-x64.zip'
PACKAGE = OUT / ('ochd-' + VERSION + '-full-client-win-x64.zip')
INSTALL = '''OCHD · GTNH 2.9.0 正式版完整客户端包（Windows x64）

这是一份 OCHD 附加包，不是 GTNH 整合包本体。包含 OCHD 模组、完整 Chromium/CEF
原生运行时、MCP 连接程序及说明。无需再下载浏览器运行时或运行联网安装器。

安装：
1. 关闭 Minecraft。打开新 GTNH 2.9.0 正式版实例的游戏目录，也就是含有
   mods、config、options.txt 的目录，Prism 中通常是实例下的 .minecraft。
2. 将本压缩包里的全部内容解压到该目录，合并 mods 文件夹。
   解压后应看到 mods/oc-hd-panel-VERSION.jar 和 ochd-web-runtime/cef-host.jar。
   不要只把压缩包或整个运行时文件夹放进 mods。
3. 若实例装过 OCHD，先将旧 oc-hd-panel-*.jar 移出 mods，只启用本版。
4. 使用 Java 21 或 Java 25 启动新实例（推荐沿用正式整合包的 Java 25）。
5. 服务器只安装相同版本的 OCHD JAR，不需要客户端浏览器运行时。

客户端与服务器均需使用官方正式版的 OC 1.12.68-GTNH。本包没有包含或替换
GTNH/OC/AE2 本体。保留屏幕、RF/EU/OC/AE 供电、MCP 机箱及大容量硬盘功能。

MCP（可选）：
游戏启动后自动生成 config/ochd-mcp-client.json。由玩家放置 MCP 接入机箱，
用 OC 线缆连接电脑。Python 3.9+ 另行安装（本包不附带 Python），连接入口是
ochd-mcp/ochd_mcp.py；参见 ochd-mcp/INSTALL.md 和配置示例。
若是迁移原存档且助手已安装，原助手可沿用；首次安装助手需要停止并重启 OC。
本包不含你的连接令牌、世界存档、旧 OC 磁盘或 BEC 私人配置。

页面：
网页由 OC 电脑中的 Lua 程序发布；新客户端会接收服务器发布的页面。
全新世界不会凭空出现 BEC 自动化程序，需要另外安装相应程序。
oc/ 目录附带通用演示安装脚本；演示不是 BEC 正式自动化。

验证记录在 ochd-mcp/validation.json。64 槽与 BEC 批量操作仍待联机实测。
'''.replace('VERSION', VERSION)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(MOD) as mod:
        assert json.loads(mod.read('mcmod.info'))[0]['version'] == VERSION
    reports = [ET.parse(p).getroot() for p in (ROOT / 'build/test-results/test').glob('TEST-*.xml')]
    tests = sum(int(report.attrib['tests']) for report in reports)
    assert tests > 0 and all(report.attrib['failures'] == report.attrib['errors'] == '0' for report in reports), 'Run Gradle test before packaging'
    validation = {'version': VERSION, 'target': 'GTNH 2.9.0', 'javaTestsPassed': tests,
                  'groupAdapterInGameTested': False}
    entries = {}
    with zipfile.ZipFile(PACKAGE, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as dest:
        def put(name, data):
            assert name not in entries and not name.startswith('/') and '..' not in name.split('/')
            entries[name] = hashlib.sha256(data).hexdigest()
            dest.writestr(name, data)

        with zipfile.ZipFile(RUNTIME) as runtime:
            for name in runtime.namelist():
                if not name.endswith('/'):
                    put(name, runtime.read(name))
        put('mods/' + MOD.name, MOD.read_bytes())
        put('INSTALL-安装说明.txt', INSTALL.encode('utf-8-sig'))
        files = {
            'docs/GTNH_2.9.0.md': 'ochd-mcp/GTNH_2.9.0.md',
            'docs/GROUP_ADAPTER.md': 'ochd-mcp/GROUP_ADAPTER.md',
            'docs/MCP.md': 'ochd-mcp/INSTALL.md',
            'mcp/ochd_mcp.py': 'ochd-mcp/ochd_mcp.py',
            'mcp/codex-config.example.toml': 'ochd-mcp/codex-config.example.toml',
            'LICENSE': 'OCHD-LICENSE.txt',
            'docs/HTML_APPS.md': 'OCHD-HTML-README.md',
            'oc/install_web_demo.lua': 'oc/install_web_demo.lua',
            'oc/automation_demo.lua': 'oc/automation_demo.lua',
        }
        for source, target in files.items():
            put(target, (ROOT / source).read_bytes())
        put('ochd-mcp/validation.json', (json.dumps(validation, indent=2) + '\n').encode())
        put('OCHD-FILES-SHA256.json', (json.dumps(entries, indent=2) + '\n').encode())
    print('Created', PACKAGE, PACKAGE.stat().st_size, 'bytes', flush=True)
    with zipfile.ZipFile(PACKAGE) as check:
        assert check.testzip() is None
        manifest = json.loads(check.read('OCHD-FILES-SHA256.json'))
        assert set(check.namelist()) == set(manifest) | {'OCHD-FILES-SHA256.json'}
        for name, expected in manifest.items():
            assert hashlib.sha256(check.read(name)).hexdigest() == expected, name
        required = ['ochd-web-runtime/native/libcef.dll', 'ochd-web-runtime/native/jcef.dll',
                    'ochd-web-runtime/cef-host.jar', 'ochd-mcp/ochd_mcp.py', 'mods/' + MOD.name]
        for name in required:
            assert name in manifest, name
        assert not any('ochd-mcp-client.json' in n or n.startswith(('saves/', 'config/')) for n in manifest)
    sums = OUT / 'SHA256SUMS.txt'
    existing = [line for line in sums.read_text().splitlines() if not line.endswith(PACKAGE.name)] if sums.exists() else []
    existing.append(hashlib.sha256(PACKAGE.read_bytes()).hexdigest() + '  ' + PACKAGE.name)
    sums.write_text('\n'.join(existing) + '\n', encoding='utf-8')
    print('Verified CRC and SHA-256 of', len(entries), 'files; full native runtime included.')


if __name__ == '__main__':
    main()
