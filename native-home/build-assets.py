"""Compile the real-data Android home from the checked orbit geometry and native controller."""
from pathlib import Path
import re
import shutil

root=Path(__file__).resolve().parent.parent
proto=root/'prototypes/orbit'
native=root/'native-home'
source=(proto/'index.html').read_text()
markup=source.split('<body>\n',1)[1].split('<script src="three.min.js">',1)[0].strip()
markup=markup.replace(' class="viz-dotted-background"','').replace('data-appearance="saturn"','data-appearance="__SEVEN_THEME__"')
markup=markup.replace('SevenDays星环首页预览，示例数据','SevenDays首页')
markup=re.sub(r'<strong>2<span>小时</span>\d+<span>分</span></strong>', '<strong>0 分钟</strong>', markup)
markup=markup.replace('<span class="so-connected"><i aria-hidden="true"></i>已连接</span>', '<button class="so-connected" type="button" aria-label="检查保护状态"><i aria-hidden="true"></i><span class="so-status-label">未连接</span></button>')
markup=markup.replace('    <div class="so-orbit-toolbar">', '    <button class="so-alert" type="button" hidden><span class="so-alert-label"></span><span aria-hidden="true">›</span></button>\n    <div class="so-orbit-toolbar">')
markup=markup.replace('<button class="so-selected" type="button"', '<p class="so-empty">尚未添加应用</p>\n    <button class="so-selected" type="button" hidden')
markup=markup.replace('1 / 7','0 / 0').replace('<span>11月6日可调整</span>','<span class="so-lock-label"></span>')
markup=markup.replace('哔哩哔哩','未选择应用').replace('每天 90 分钟','').replace('18:01','00:00').replace('已用 71 分钟','已用 0 分钟')
markup=re.sub(r'    <div class="so-toast".*?</div>\n','',markup)
css=(proto/'styles.css').read_text()+'\n'+(native/'home.css').read_text()
boot='''window.addEventListener('error',()=>{try{window.SevenDaysNative.postMessage('{"action":"fallback"}');}catch(error){}});window.addEventListener('unhandledrejection',()=>{try{window.SevenDaysNative.postMessage('{"action":"fallback"}');}catch(error){}});'''
scripts=[(proto/n).read_text() for n in ['three.min.js','planet-themes.js','orbit-renderer.js','sheet-motion.js']]+[(native/'home.js').read_text()]
csp="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
html='<!doctype html>\n<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="light dark"><meta http-equiv="Content-Security-Policy" content="'+csp+'"><title>SevenDays</title><style>\n'+css+'\n</style></head><body>\n'+markup+'\n<script>'+boot+'</script>\n'+'\n'.join('<script>\n'+s+'\n</script>' for s in scripts)+'\n</body></html>\n'
target=root/'app/src/main/assets/orbit/index.html'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(html)
shutil.copy2(proto/'THREE-LICENSE.txt',target.parent/'THREE-LICENSE.txt')
print('Built offline Android home:',target.stat().st_size,'bytes')
