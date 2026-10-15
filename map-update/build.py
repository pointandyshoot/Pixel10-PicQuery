#!/usr/bin/env python3
"""Reproducible, narrowly scoped 0.3.1 recovery build. See README.md."""
import argparse, hashlib, json, os, subprocess, sys, zipfile
from pathlib import Path

BASE_SHA='f5acbf5ffdf25ca9f079eccd3b78168e4ce4610592b9d8b4f918f8a553c02c05'
p=argparse.ArgumentParser()
p.add_argument('--base-apk',type=Path,required=True)
p.add_argument('--tools',type=Path,required=True)
p.add_argument('--keystore',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--work',type=Path,required=True)
a=p.parse_args(); a.tools=a.tools.resolve();a.work=a.work.resolve();a.output=a.output.resolve()
assert hashlib.sha256(a.base_apk.read_bytes()).hexdigest()==BASE_SHA,'Wrong recovery base APK'
src=Path(__file__).resolve().parent
def run(*args):subprocess.run([str(x) for x in args],check=True)
a.work.mkdir(parents=True,exist_ok=True)
decoded=a.work/'decoded'
run('java','-jar',a.tools/'apktool.jar','d','-f','-o',decoded,a.base_apk)
app=decoded/'smali_classes3/au/pointandyshoot/picquery'
original={x.name:hashlib.sha256(x.read_bytes()).hexdigest() for x in app.glob('*.smali')}
run(sys.executable,src/'patch.py',decoded)
changed=[name for name,digest in original.items() if not (app/name).exists() or hashlib.sha256((app/name).read_bytes()).hexdigest()!=digest]
allowed={'PhotoMapKt.smali','PhotoMapKt$$ExternalSyntheticLambda2.smali','PhotoMapKt$$ExternalSyntheticLambda3.smali','PhotoClusters.smali','BuildConfig.smali','ComposableSingletons$MainActivityKt.smali'}
assert set(changed)<=allowed,('Unexpected non-map changes',set(changed)-allowed)
(a.work/'integrity.json').write_text(json.dumps({'base_apk_sha256':BASE_SHA,'original_app_classes':len(original),'modified_or_replaced_classes':changed,'unchanged_app_classes':len(original)-len(changed)},indent=2)+'\n')
classes=a.work/'classes';classes.mkdir(exist_ok=True)
android=a.tools/'sdk/android-36/android.jar';bt=a.tools/'sdk/android-16'
cp=os.pathsep.join(map(str,[android,a.tools/'osmdroid.jar',a.tools/'kotlin.jar']))
run('java','com.sun.tools.javac.Main','--release','17','-cp',cp,'-d',classes,
    *list((src/'stubs').rglob('*.java')),*list((src/'src').rglob('*.java')))
jar=a.work/'map-classes.jar'
with zipfile.ZipFile(jar,'w') as z:
    for f in classes.rglob('*.class'):
        if f.name!='LocatedPhoto.class':z.write(f,f.relative_to(classes))
dex=a.work/'dex';dex.mkdir(exist_ok=True)
run('java','-cp',a.tools/'r8.jar','com.android.tools.r8.D8','--min-api','36','--lib',android,'--output',dex,jar)
rebuilt=a.work/'rebuilt.apk'
run('java','-jar',a.tools/'apktool.jar','b',decoded,'-o',rebuilt)
combined=a.work/'combined.apk'
with zipfile.ZipFile(a.base_apk) as original,zipfile.ZipFile(rebuilt) as changed,zipfile.ZipFile(combined,'w') as out:
    for item in original.infolist():
        if item.filename.startswith('META-INF/') and item.filename.endswith(('.RSA','.SF','.MF','.DSA','.EC')):continue
        data=changed.read(item.filename) if item.filename in ['classes3.dex','AndroidManifest.xml'] else original.read(item.filename)
        out.writestr(item,data)
    out.write(dex/'classes.dex','classes7.dex',compress_type=zipfile.ZIP_STORED)
aligned=a.work/'aligned.apk'
run(bt/'zipalign','-f','-P','16','4',combined,aligned)
a.output.parent.mkdir(parents=True,exist_ok=True)
env=dict(os.environ,PIXEL_KEY_PASSWORD=os.environ.get('PIXEL_KEY_PASSWORD','android'))
subprocess.run(['java','-jar',str(bt/'lib/apksigner.jar'),'sign','--ks',str(a.keystore),
    '--ks-key-alias','androiddebugkey','--ks-pass','env:PIXEL_KEY_PASSWORD','--key-pass','env:PIXEL_KEY_PASSWORD',
    '--out',str(a.output),str(aligned)],check=True,env=env)
run('java','-jar',bt/'lib/apksigner.jar','verify',a.output)
run(bt/'zipalign','-c','-P','16','4',a.output)
print('APK:',a.output)
print('SHA256:',hashlib.sha256(a.output.read_bytes()).hexdigest())
