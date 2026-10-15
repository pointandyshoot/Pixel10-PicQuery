#!/usr/bin/env python3
"""Apply the reviewed map-only changes to an apktool decode of the 0.3.0 recovery APK.

The base remains a required input, not a downloaded or silently substituted binary.
New Java classes are compiled separately and included as classes7.dex.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
base=root/'smali_classes3/au/pointandyshoot/picquery'
def replace(path, old, new):
    s=path.read_text()
    assert s.count(old)==1, (path.name, 'expected exactly one hook')
    path.write_text(s.replace(old,new))

factory=base/'PhotoMapKt$$ExternalSyntheticLambda2.smali'
replace(factory, '    move-result-object p1\n\n    return-object p1',
'''    move-result-object p1

    invoke-static {p1}, Lau/pointandyshoot/picquery/MapShell;->wrap(Lorg/osmdroid/views/MapView;)Landroid/view/View;

    move-result-object p1

    return-object p1''')
update=base/'PhotoMapKt$$ExternalSyntheticLambda3.smali'
replace(update, '    check-cast p1, Lorg/osmdroid/views/MapView;',
'''    invoke-static {p1}, Lau/pointandyshoot/picquery/MapShell;->unwrap(Ljava/lang/Object;)Lorg/osmdroid/views/MapView;

    move-result-object p1''')
replace(base/'PhotoMapKt.smali', '    invoke-virtual {p3, p0}, Lorg/osmdroid/views/MapView;->setUseDataConnection(Z)V',
'    invoke-static {p3, p0}, Lau/pointandyshoot/picquery/MapShell;->sync(Lorg/osmdroid/views/MapView;Z)V')
# The Java replacement retains the original ABI. Do not package two class definitions.
(base/'PhotoClusters.smali').unlink()
for p in base.glob('*.smali'):
    s=p.read_text()
    if '0.3.0' in s: p.write_text(s.replace('0.3.0','0.3.1'))
p=root/'apktool.yml';s=p.read_text();assert '  versionCode: 3\n' in s
p.write_text(s.replace('  versionCode: 3\n','  versionCode: 4\n').replace('  versionName: 0.3.0\n','  versionName: 0.3.1\n'))
print('Applied map-only hooks and version 0.3.1 (4).')
