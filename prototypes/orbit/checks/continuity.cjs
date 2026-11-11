const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const context={window:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../orbit-renderer.js'),'utf8'),context);
const {visibleItems,STEP}=context.window.SevenOrbitRenderer;let checked=0;
for(const n of [1,2,3,6,7])for(let i=0;i<n;i++){
 const seam=-(i+n/2)*STEP,epsilon=1e-8;
 const a=visibleItems(seam-epsilon,n).find(x=>x.index===i),b=visibleItems(seam+epsilon,n).find(x=>x.index===i);
 assert(a&&b);const distance=Math.hypot(Math.sin(a.theta)-Math.sin(b.theta),Math.cos(a.theta)-Math.cos(b.theta));
 assert(distance<1e-5,'discontinuous seam: '+n+' apps / index '+i);checked++;
}
console.log('PASS '+checked+' small-count seam continuity checks');
