#!/usr/bin/env python3
"""Build without Gradle: Python 3, JDK 17, Android SDK 35 / Build Tools 35.0.0."""
import os, pathlib, shutil, subprocess, sys, zipfile
from build_signing import jdk_tool, sign_apk
ROOT=pathlib.Path(__file__).resolve().parent
sdk=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or '')
if not str(sdk) or not (sdk/'build-tools'/'35.0.0').exists():
    sys.exit('Set ANDROID_SDK_ROOT to Android SDK; install platforms;android-35 and build-tools;35.0.0 in Android Studio.')
bt=sdk/'build-tools'/'35.0.0'
jar=sdk/'platforms'/'android-35'/'android.jar'
if not jar.exists():
    matches=list((sdk/'platforms'/'android-35').rglob('android.jar'))
    if not matches: sys.exit('android.jar is missing')
    jar=matches[0]
out=ROOT/'build-local'
if out.exists():shutil.rmtree(out)
for folder in ['generated','classes','dex']:(out/folder).mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
def tool(name):
    suffix='.exe' if os.name=='nt' else ''
    return bt/(name+suffix)
java=jdk_tool('java')
run(sys.executable,ROOT/'native-home/build-assets.py')
run(tool('aapt2'),'compile','--dir',ROOT/'app/src/main/res','-o',out/'resources.zip')
run(tool('aapt2'),'link','-o',out/'base.apk','--manifest',ROOT/'app/src/main/AndroidManifest.xml','-I',jar,'--java',out/'generated','--min-sdk-version','26','--target-sdk-version','35','--version-code','13','--version-name','0.6.5',out/'resources.zip')
sources=[f for folder in [ROOT/'app/src/main/java',out/'generated'] for f in folder.rglob('*.java') if f.is_file() and not any(part.startswith('.') for part in f.relative_to(folder).parts)]
run(java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','17','-target','17','-classpath',jar,'-d',out/'classes',*sources)
run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','26','--output',out/'dex',*[f for f in (out/'classes').rglob('*.class') if f.is_file() and not any(x.startswith('.') for x in f.relative_to(out/'classes').parts)])
with zipfile.ZipFile(out/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
    for f in (out/'dex').glob('*.dex'):z.write(f,f.name)
    for f in (ROOT/'app/src/main/assets').rglob('*'):
        if f.is_file():z.write(f,'assets/'+f.relative_to(ROOT/'app/src/main/assets').as_posix())
run(tool('zipalign'),'-f','4',out/'base.apk',out/'aligned.apk')
suffix='' if os.environ.get('SEVENDAYS_KEYSTORE') else '-debug'
apk=ROOT/f'SevenDays-0.6.5{suffix}.apk'
sign_apk(bt,out/'aligned.apk',apk)
run(java,'-jar',bt/'lib/apksigner.jar','verify','--verbose',apk)
print('APK:',apk)
