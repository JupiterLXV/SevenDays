window.SevenSheetMotion=(()=>{
  'use strict';
  const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
  function bind({sheet,chrome,scrim,isReduced,onDismiss}){
    let gesture=null,offset=0,frame=0,closing=false,suppressClick=false,generation=0;
    function paint(value){offset=value;sheet.style.transformOrigin='50% 100%';sheet.style.transform=`translate3d(0,${Math.max(0,value)}px,0) scaleY(${value<0?1+Math.min(.025,-value/Math.max(1,sheet.offsetHeight)):1})`;scrim.style.opacity=String(1-.8*clamp(value/Math.max(1,sheet.offsetHeight),0,1));}
    function stop(){generation++;if(frame)cancelAnimationFrame(frame);frame=0;}
    function release(){if(!gesture)return;const p=gesture;gesture=null;try{chrome.releasePointerCapture(p.id);}catch(error){}}
    function reset(){release();stop();closing=false;suppressClick=false;sheet.inert=false;sheet.style.willChange='';paint(0);}
    function settle(dismiss){stop();closing=dismiss;sheet.inert=dismiss;const start=offset,target=dismiss?Math.max(sheet.offsetHeight+24,offset):0;if(isReduced()||(!dismiss&&Math.abs(start)<.1)){paint(target);if(dismiss)onDismiss();return;}sheet.style.willChange='transform';const stamp=performance.now(),duration=dismiss?180:240,token=generation;
      const tick=now=>{if(token!==generation)return;const p=clamp((now-stamp)/duration,0,1);paint(start+(target-start)*(1-Math.pow(1-p,3)));if(p<1)frame=requestAnimationFrame(tick);else{frame=0;sheet.style.willChange='';if(dismiss)onDismiss();}};frame=requestAnimationFrame(tick);
    }
    function dismiss(){if(closing)return;release();settle(true);}
    chrome.addEventListener('pointerdown',e=>{if(e.button>0||closing||e.target.closest('.so-close'))return;if(gesture){release();settle(false);return;}stop();gesture={id:e.pointerId,x:e.clientX,y:e.clientY,base:offset,grip:!!e.target.closest('.so-grip'),dragged:false,rejected:false,samples:[{y:e.clientY,t:performance.now()}]};chrome.setPointerCapture(e.pointerId);});
    chrome.addEventListener('pointermove',e=>{if(!gesture||e.pointerId!==gesture.id)return;const g=gesture,dx=e.clientX-g.x,dy=e.clientY-g.y;if(!g.dragged&&!g.rejected&&Math.hypot(dx,dy)>7){if(Math.abs(dy)>Math.abs(dx)*1.15)g.dragged=true;else g.rejected=true;}if(!g.dragged)return;e.preventDefault();const now=performance.now();g.samples.push({y:e.clientY,t:now});g.samples=g.samples.filter(p=>now-p.t<=110);const travel=g.base+dy;paint(travel<0?-Math.min(18,-travel*.18):Math.min(sheet.offsetHeight+24,travel));});
    function end(e,cancelled){if(!gesture||gesture.id!==e.pointerId)return;const g=gesture,now=performance.now();let speed=0;if(!cancelled&&g.samples.length>1&&now-g.samples.at(-1).t<100){const first=g.samples[0],last=g.samples.at(-1);speed=(last.y-first.y)/Math.max(.016,(last.t-first.t)/1000);}release();if(g.dragged||g.rejected||cancelled||g.grip){suppressClick=true;setTimeout(()=>suppressClick=false,0);}const threshold=clamp(sheet.offsetHeight*.24,72,140);settle(!cancelled&&g.dragged&&((offset>=threshold&&speed>-450)||(offset>20&&speed>900)));}
    chrome.addEventListener('pointerup',e=>end(e,false));chrome.addEventListener('pointercancel',e=>end(e,true));chrome.addEventListener('lostpointercapture',e=>{if(gesture?.id===e.pointerId)end(e,true);});
    chrome.addEventListener('click',e=>{if(suppressClick||closing){e.preventDefault();e.stopImmediatePropagation();}},true);
    return {reset,dismiss,cancel:()=>{release();settle(false);},get moving(){return !!frame||!!gesture;},get closing(){return closing;}};
  }
  return {bind};
})();
