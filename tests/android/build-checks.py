from pathlib import Path
import subprocess as s,zipfile,os,sys
root=Path(__file__).resolve().parents[2];sdk=Path(os.environ['ANDROID_SDK_ROOT']);bt=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
sys.path.insert(0,str(root))
from build_signing import sign_apk
if not jar.exists():jar=next((sdk/'platforms/android-35').rglob('android.jar'))
def run(*a):s.run([str(x) for x in a],check=True)
for kind in ['checks','probe','home','second','third']:
 out=root/'build-runtime-tests'/kind;out.mkdir(parents=True,exist_ok=True);(out/'classes').mkdir(exist_ok=True);(out/'dex').mkdir(exist_ok=True)
 src=root/'tests/android';manifest=src/('AndroidManifest.xml' if kind=='checks' else ('probe/AndroidManifest.xml' if kind in ['probe','second','third'] else 'home/AndroidManifest.xml'));source=src/('RuntimeChecks.java' if kind=='checks' else ('probe/Probe.java' if kind in ['probe','second','third'] else 'home/TestHome.java'))
 if kind in ['second','third']:
  text=manifest.read_text().replace('package="cn.sevendays.probe"','package="cn.sevendays.probe.'+kind+'"').replace('android:name=".Probe"','android:name="cn.sevendays.probe.Probe"').replace('android:label="验证应用"','android:label="验证应用'+('二' if kind=='second' else '三')+'"');manifest=out/'AndroidManifest.xml';manifest.write_text(text)
 run(bt/'aapt2','link','-o',out/'base.apk','--manifest',manifest,'-I',jar)
 run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','17','-target','17','-classpath',str(jar)+os.pathsep+str(root/'build-local/classes'),'-d',out/'classes',*([source,src/'MotionChecks.java',src/'FlowChecks.java'] if kind=='checks' else [source]))
 run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--classpath',root/'build-local/classes','--min-api','26','--output',out/'dex',*[f for f in (out/'classes').rglob('*.class') if f.is_file() and not any(x.startswith('.') for x in f.relative_to(out/'classes').parts)])
 with zipfile.ZipFile(out/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
  for f in (out/'dex').glob('*.dex'):z.write(f,f.name)
 run(bt/'zipalign','-f','4',out/'base.apk',out/'aligned.apk')
 sign_apk(bt,out/'aligned.apk',out/(kind+'.apk'))
