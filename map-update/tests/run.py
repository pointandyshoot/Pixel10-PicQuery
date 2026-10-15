#!/usr/bin/env python3
"""Run after Gradle resolveVerification; tools supplies osmdroid.jar and kotlin.jar."""
import argparse, os, subprocess, sys, urllib.parse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--tools',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
here=Path(__file__).resolve().parent;src=here.parent;out=a.work.resolve();out.mkdir(parents=True,exist_ok=True)
cp=(here/'classpath.txt').read_text()+os.pathsep+os.pathsep.join(str(a.tools/n) for n in ['osmdroid.jar','kotlin.jar'])
subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',cp,'-d',str(out),*map(str,(src/'stubs').rglob('*.java')),*map(str,(src/'src').rglob('*.java')),str(here/'MapUiTest.java')],check=True)
args=['java','-Drobolectric.dependency.repo.url=https://repo1.maven.org/maven2']
if os.environ.get('HTTPS_PROXY'):
 u=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
 for proto in ['http','https']:args+=['-D'+proto+'.proxyHost='+u.hostname,'-D'+proto+'.proxyPort='+str(u.port)]
sys.exit(subprocess.run(args+['-cp',cp+os.pathsep+str(out),'org.junit.runner.JUnitCore','MapUiTest']).returncode)
