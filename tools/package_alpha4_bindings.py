"""Overlay binding-related classes only; preserve the supplied alpha4 binary elsewhere."""
import hashlib
import json
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'libs/oc-hd-panel-0.3.0-alpha4.jar'
COMPILED=ROOT/'build/libs/oc-hd-panel-0.4.0-alpha8-chromium.jar'
OUT=ROOT/'build/distributions'
EXPECTED='6ff5a5bd5a8d7caf0d544d29fb9a8140d3c241a165cea3efa9bb01cfb8578b69'
VERSION='0.4.0-alpha8-chromium'

def overlay(name):
    if name in ["assets/ochdpanel/lang/zh_CN.lang","assets/ochdpanel/lang/en_US.lang"]:return True
    stems=['WebMappedFrames','PanelAEGrid','PanelBlock','PanelPower','PanelSettingsPacket','client/ScreenSettingsGui','WebViewport','WebFrameCodec','WebCommandQueue','ActionPacket','HdPanelMod','PanelModel','PanelTile','client/ClientProxy','client/DashboardPainter','client/PanelGui','client/BindingPainter','CommonProxy','WebAppModel','WebAssetRequest','WebAssetPacket','client/PanelRenderer','client/WebHostProcess','client/WebScreens','client/WebPanelGui']
    for stem in stems:
        p='dev/bec/hdpanel/'+stem
        if name==p+'.class'or name.startswith(p+'$'):return True
    return name in ['dev/bec/hdpanel/BindingModel.class','dev/bec/hdpanel/client/BindingGui.class']

def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==EXPECTED,'Unexpected alpha4 base'
    OUT.mkdir(parents=True,exist_ok=True);output=OUT/f'oc-hd-panel-{VERSION}.jar'
    with zipfile.ZipFile(BASE)as base,zipfile.ZipFile(COMPILED)as compiled:
        entries={n:base.read(n)for n in base.namelist()};replaced=[]
        for name in compiled.namelist():
            if overlay(name):entries[name]=compiled.read(name);replaced.append(name)
        info=json.loads(base.read('mcmod.info'));info[0]['version']=VERSION
        entries['mcmod.info']=json.dumps(info,ensure_ascii=False,indent=2).encode()
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED)as z:
            for name,data in entries.items():z.writestr(name,data)
        with zipfile.ZipFile(output)as result:
            preserved=[]
            for name in base.namelist():
                if not overlay(name)and name!='mcmod.info':
                    assert result.read(name)==base.read(name),'Changed alpha4 entry: '+name
                    preserved.append(name)
        evidence={'base_sha256':EXPECTED,'result_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
            'version':VERSION,'replaced_or_added_entries':replaced,'unchanged_original_entries':preserved,'hardware_tested':False}
        (OUT/'alpha4-preservation.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
    bundle=OUT/'oc-hd-panel-alpha4-bindings-test-package.zip'
    with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED)as z:
        z.write(output,'mods/'+output.name)
        for name in ['ochd_bindings.lua','bec_v2_setup_hd.lua','bec_hd.lua','install_bindings.lua']:
            z.write(ROOT/'oc'/name,'oc/'+name)
        z.write(ROOT/'docs/BINDINGS.md','BINDINGS.md');z.write(OUT/'alpha4-preservation.json','alpha4-preservation.json')
    assets=[output,bundle,OUT/'alpha4-preservation.json']
    (OUT/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'for p in assets),encoding='utf-8')
    print('Alpha4 preservation:',len(preserved),'unchanged entries;',len(replaced),'binding classes')
    print(output)

if __name__=='__main__':main()
