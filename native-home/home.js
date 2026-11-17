(()=>{
  'use strict';
  const root=document.getElementById('sevendays-refined-preview'),$=s=>root.querySelector(s),stage=$('.so-stage'),Kit=window.SevenThemeKit,STEP=window.SevenOrbitRenderer.STEP;
  const post=(action,value='')=>{try{window.SevenDaysNative.postMessage(JSON.stringify({action,value}));}catch(error){}};
  const decode=encoded=>JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(encoded),c=>c.charCodeAt(0))));
  const dark=matchMedia('(prefers-color-scheme:dark)'),systemReduced=matchMedia('(prefers-reduced-motion:reduce)');
  let apps=[],engine,themeId='saturn',colors={},selected=0,angle=0,velocity=0,tween=null,pointer=null,frame=0,lastFrame=0,wheelTimer=0,paused=false,reduced=false,nativeReduced=false,restoreFocus=null,structure='',received=false,lastAnnounced='',pageLock=null;
  const images=new Map(),clamp=(x,a,b)=>Math.max(a,Math.min(b,x)),period=()=>Math.max(1,apps.length)*STEP;
  const hourglass=$('.so-hourglass'),sandTop=$('.so-sand-top'),sandBase=$('.so-sand-base');
  let lastSandRatio=-1,lastSandState='',sandMotion;
  const wrap=a=>((a+period()/2)%period()+period())%period()-period()/2;
  const nearest=()=>apps.length?((Math.round(-angle/STEP)%apps.length)+apps.length)%apps.length:0;
  const appTime=a=>a.quota===0?'全天禁用':a.remaining<=0?'已到限':a.remaining>=3600?`${Math.floor(a.remaining/3600)}:${String(Math.floor(a.remaining%3600/60)).padStart(2,'0')}:${String(a.remaining%60).padStart(2,'0')}`:`${String(Math.floor(a.remaining/60)).padStart(2,'0')}:${String(a.remaining%60).padStart(2,'0')}`;
  function readColors(){const cs=getComputedStyle(root),probe=document.createElement('span');probe.hidden=true;root.append(probe);for(const [key,variable]of Object.entries({surface:'--so-surface',ink:'--so-ink',muted:'--so-muted',soft:'--so-soft',primary:'--so-primary'})){probe.style.color=cs.getPropertyValue(variable).trim();colors[key]=getComputedStyle(probe).color;}probe.remove();}
  function drawIcon(ctx,a,x,y,size){ctx.fillStyle=colors.surface;ctx.fillRect(x,y,size,size);if(!a)return;const image=images.get(a.pkg);if(image?.complete&&image.naturalWidth){ctx.drawImage(image,x,y,size,size);return;}ctx.save();ctx.fillStyle=colors.soft;ctx.beginPath();ctx.roundRect(x,y,size,size,size*.23);ctx.fill();ctx.fillStyle=colors.primary;ctx.font=`500 ${size*.48}px system-ui`;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(Array.from(a.name)[0]||'·',x+size/2,y+size*.52);ctx.restore();}
  function paintIcons(){if(apps.length)drawIcon($('.so-current-icon').getContext('2d'),apps[selected],0,0,96);}
  function draw(){if(engine){engine.render(angle,selected);root.dataset.renderer=engine.kind;}}
  const gain=()=>Math.min(1,apps.length/7),moving=()=>!!tween||!!pointer?.dragged||Math.abs(velocity)>.025;
  function motionLabel(){const active=moving();$('.so-brake').textContent=active?'Ⅱ':'▷';$('.so-brake').setAttribute('aria-label',active?'立即停止旋转':'转动卡片');}
  function updateSand(app){
    const ratio=app.quota?clamp(app.remaining/(app.quota*60),0,1):0;
    if(lastSandRatio!==ratio){
      // A narrowing chamber holds less sand per row near the waist.
      const level=Math.sqrt(ratio),upper=20*level,lower=19*(1-level);
      sandTop.setAttribute('y',25-upper);sandTop.setAttribute('height',upper);
      sandBase.setAttribute('y',47-lower);sandBase.setAttribute('height',lower);
      lastSandRatio=ratio;
    }
    if(!moving()){
      const state=app.pkg+'|'+(app.remaining>0?'available':'empty');
      if(lastSandState&&state!==lastSandState&&!paused&&!reduced&&!document.hidden&&hourglass.animate){
        if(sandMotion)sandMotion.cancel();
        sandMotion=hourglass.animate([{opacity:.55,transform:'translateY(-2px)'},{opacity:1,transform:'translateY(0)'}],{duration:210,easing:'ease-out'});
      }
      lastSandState=state;
    }
  }
  function announce(){const a=apps[selected];if(!a)return;const text=`已选 ${a.name}，${appTime(a)}`;if(text!==lastAnnounced){$('.so-sr').textContent=text;lastAnnounced=text;}}
  function updateSelection(force=false){const next=nearest(),changed=next!==selected;selected=next;const has=apps.length>0;$('.so-selected').hidden=!has;$('.so-empty').hidden=has;$('.so-controls').hidden=!has;for(const s of ['.so-prev','.so-next','.so-brake'])$(s).disabled=apps.length<2;stage.style.touchAction=apps.length<2?'pan-y':'none';if(!has){lastSandState="";return;}const a=apps[selected];$('.so-count').textContent=`${selected+1} / ${apps.length}`;$('.so-selection-name').textContent=a.name;$('.so-selection-quota').textContent=a.quota===0?'全天禁用':`每天 ${a.quota} 分钟`;$('.so-selection-time').textContent=appTime(a);$('.so-selection-time').classList.toggle('so-exhausted',!a.remaining);$('.so-remaining-label').textContent=a.quota===0?'规则':a.remaining?'剩余时间':'今日额度';updateSand(a);$('.so-used-label').textContent=a.usedLabel;$('.so-lock-label').textContent=a.lockLabel;$('.so-selected').setAttribute('aria-label',`${a.name}，${a.remaining>0?'剩余 ':''}${appTime(a)}，查看规则`);if(changed||force)paintIcons();if(changed)post('select',a.pkg);}
  function freeze(){if(pointer)try{stage.releasePointerCapture(pointer.id);}catch(error){}pointer=null;velocity=0;tween=null;clearTimeout(wheelTimer);if(frame)cancelAnimationFrame(frame);frame=0;lastFrame=0;updateSelection();motionLabel();draw();}
  function wake(){if(!frame&&!paused&&!document.hidden&&$('.so-modal').hidden){lastFrame=performance.now();frame=requestAnimationFrame(tick);}}
  function tick(now){frame=0;const dt=clamp((now-lastFrame)/1000,0,.04);lastFrame=now;if(tween){const p=clamp((now-tween.start)/tween.duration,0,1);angle=wrap(tween.from+tween.delta*(1-Math.pow(1-p,3)));if(p===1){const done=tween.done;tween=null;velocity=0;updateSelection();motionLabel();draw();announce();if(done)done();return;}}else if(!pointer&&Math.abs(velocity)>.025){angle=wrap(angle+velocity*dt);velocity*=Math.exp(-1.2*dt);if(Math.abs(velocity)<.07){velocity=0;focusApp(nearest());return;}}updateSelection();motionLabel();draw();if(moving())frame=requestAnimationFrame(tick);}
  function focusApp(index,done){if(!apps.length)return;index=((index%apps.length)+apps.length)%apps.length;velocity=0;const target=-index*STEP,delta=wrap(target-angle);if(reduced||paused||document.hidden||Math.abs(delta)<.002){angle=wrap(target);tween=null;updateSelection(true);motionLabel();draw();announce();if(done)done();return;}tween={from:angle,delta,start:performance.now(),duration:clamp(260+Math.abs(delta)*32,260,580),done};wake();}
  function setAppearance(id,notify=false){if(!Object.prototype.hasOwnProperty.call(Kit.appearances,id))return;freeze();themeId=id;root.dataset.appearance=id;$('.so-theme-name').textContent=Kit.appearances[id].name;$('.so-theme').setAttribute('aria-label','选择外观主题，当前'+Kit.appearances[id].name);readColors();if(engine)engine.setTheme(id);updateSelection(true);draw();if(notify)post('theme',id);}
  function lockPage(){if(pageLock)return;pageLock={html:document.documentElement.style.overflow,body:document.body.style.overflow,y:scrollY};document.documentElement.style.overflow='hidden';document.body.style.overflow='hidden';}
  function unlockPage(){if(!pageLock)return;const saved=pageLock;pageLock=null;document.documentElement.style.overflow=saved.html;document.body.style.overflow=saved.body;window.scrollTo(0,saved.y);}
  function showModal(title,html,setup){sheetMotion.reset();freeze();lockPage();restoreFocus=document.activeElement;$('#so-sheet-title').textContent=title;$('.so-sheet-content').innerHTML=html;$('.so-modal').hidden=false;for(const child of $('.so-phone').children)if(!child.classList.contains('so-modal')&&!child.classList.contains('so-sr'))child.inert=true;$('.so-sheet-content').scrollTop=0;if(setup)setup($('.so-sheet-content'));paintIcons();$('.so-close').focus({preventScroll:true});post('modal','open');}
  function closeModal(animate=false){if(animate===true&&!$('.so-modal').hidden){sheetMotion.dismiss();return;}sheetMotion.reset();$('.so-modal').hidden=true;unlockPage();$('.so-sheet-content').replaceChildren();for(const child of $('.so-phone').children)child.inert=false;if(restoreFocus?.isConnected)restoreFocus.focus({preventScroll:true});restoreFocus=null;post('modal','closed');}
  function themes(){showModal('外观主题',Object.entries(Kit.appearances).map(([id,a])=>`<button class="so-theme-choice" type="button" data-appearance-choice="${id}" aria-pressed="${id===themeId}"><canvas width="200" height="128" aria-hidden="true"></canvas><span><strong>${a.name}</strong><small>${a.description}</small></span><b aria-hidden="true">${id===themeId?'✓':''}</b></button>`).join(''),body=>body.querySelectorAll('[data-appearance-choice]').forEach(b=>{const id=b.dataset.appearanceChoice;Kit.thumbnail(b.querySelector('canvas'),id,dark.matches);b.addEventListener('click',()=>{setAppearance(id,true);closeModal();});}));}
  function details(){if(!apps.length)return;freeze();post('detail',apps[selected].pkg);}
  const sheetMotion=window.SevenSheetMotion.bind({sheet:$('.so-sheet'),chrome:$('.so-sheet-chrome'),scrim:$('.so-scrim'),isReduced:()=>reduced,onDismiss:()=>closeModal(false)});
  $('.so-close').addEventListener('click',()=>closeModal(true));$('.so-scrim').addEventListener('click',()=>closeModal(true));$('.so-theme').addEventListener('click',themes);
  $('.so-prev').addEventListener('click',()=>{freeze();focusApp(selected-1);});$('.so-next').addEventListener('click',()=>{freeze();focusApp(selected+1);});
  $('.so-brake').addEventListener('click',()=>{if(moving()){freeze();return;}if(reduced){focusApp(selected+1);return;}velocity=1.5*gain();motionLabel();wake();});
  $('.so-selected').addEventListener('click',details);
  $('.so-settings').addEventListener('click',()=>{freeze();post('settings');});
  for(const selector of ['.so-connected','.so-alert'])$(selector).addEventListener('click',()=>{freeze();post('protection');});
  $('.so-open-list').addEventListener('click',()=>{freeze();post('layout','list');});
  $('.so-add').addEventListener('click',()=>{freeze();post('add');});
  stage.addEventListener('pointerdown',e=>{if(e.button>0||pointer||!apps.length)return;const wasMoving=moving();freeze();pointer={id:e.pointerId,x:e.clientX,y:e.clientY,lastX:e.clientX,lastY:e.clientY,lastTime:performance.now(),wasMoving,dragged:false,axis:null,samples:[]};stage.setPointerCapture(e.pointerId);});
  stage.addEventListener('pointermove',e=>{if(!pointer||e.pointerId!==pointer.id)return;const p=pointer,dx=e.clientX-p.x,dy=e.clientY-p.y;if(!p.dragged&&Math.hypot(dx,dy)>5){p.dragged=true;p.axis=Math.abs(dy)>Math.abs(dx)*1.25?'y':'x';}if(!p.dragged||apps.length<2)return;const now=performance.now(),delta=(p.axis==='y'?-(e.clientY-p.lastY)*.012:(e.clientX-p.lastX)*.012)*gain();angle=wrap(angle+delta);p.samples.push({d:delta,t:now});p.samples=p.samples.filter(s=>now-s.t<110);p.lastX=e.clientX;p.lastY=e.clientY;p.lastTime=now;updateSelection();motionLabel();draw();});
  function endPointer(e,cancelled){if(!pointer||pointer.id!==e.pointerId)return;const p=pointer;pointer=null;try{stage.releasePointerCapture(e.pointerId);}catch(error){}if(cancelled){freeze();return;}if(p.dragged){if(apps.length<2){freeze();return;}if(reduced){focusApp(nearest());return;}const now=performance.now(),samples=p.samples.filter(s=>now-s.t<110),elapsed=samples.length>1?(samples.at(-1).t-samples[0].t)/1000:.07;velocity=samples.length?clamp(samples.reduce((v,s)=>v+s.d,0)/Math.max(.04,elapsed),-6.5*gain(),6.5*gain()):0;if(now-p.lastTime>140)velocity=0;if(Math.abs(velocity)>.15){motionLabel();wake();}else focusApp(nearest());return;}if(p.wasMoving){freeze();return;}const index=engine.hit(e.clientX,e.clientY);if(index===null){freeze();return;}if(index===selected)focusApp(index,details);else focusApp(index);}
  stage.addEventListener('pointerup',e=>endPointer(e,false));stage.addEventListener('pointercancel',e=>endPointer(e,true));
  stage.addEventListener('wheel',e=>{if(!$('.so-modal').hidden||apps.length<2)return;e.preventDefault();freeze();const delta=clamp(Math.abs(e.deltaX)>Math.abs(e.deltaY)?e.deltaX:e.deltaY,-200,200);angle=wrap(angle+delta*.008*gain());updateSelection();draw();wheelTimer=setTimeout(()=>{if(reduced)focusApp(nearest());else{velocity=clamp(delta*.025,-3,3)*gain();motionLabel();wake();}},90);},{passive:false});
  root.addEventListener('keydown',e=>{if(!$('.so-modal').hidden){if(e.key==='Escape'){e.preventDefault();closeModal(true);}if(e.key==='Tab'){const list=[...$('.so-sheet').querySelectorAll('button:not(:disabled)')],first=list[0],last=list.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}return;}if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();freeze();focusApp(selected+(['ArrowRight','ArrowDown'].includes(e.key)?1:-1));}});
  function accept(encoded){const data=decode(encoded),oldPkg=apps[selected]?.pkg,key=data.apps.map(a=>a.pkg+'\0'+a.name).join('\n'),changed=key!==structure;nativeReduced=!!data.reduced;reduced=nativeReduced||systemReduced.matches;
    apps=data.apps;$('.so-summary strong').textContent=data.totalLabel;
    const protection=$('.so-connected'),alert=$('.so-alert'),running=data.protection==='RUNNING';
    const warning=data.protection==='ISSUE'||(!running&&apps.length>0);
    protection.hidden=warning;
    protection.classList.toggle('so-disconnected',!running);
    $('.so-status-label').textContent=data.statusLabel;
    protection.setAttribute('aria-label',data.statusLabel+'，'+data.statusAction);
    alert.hidden=!warning;
    $('.so-alert-label').textContent=data.statusMessage;
    $('.so-alert-action').textContent=data.statusAction;
    $('.so-empty').textContent=data.corrupt?'规则无法读取':'尚未添加应用';
    if(themeId!==data.theme)setAppearance(data.theme);
    if(changed||!received){freeze();structure=key;const wanted=apps.findIndex(a=>a.pkg===(received?oldPkg:data.selectedPkg));selected=Math.max(0,wanted);angle=wrap(-selected*STEP);engine.clearTextures();updateSelection(true);draw();if(apps.length)post('select',apps[selected].pkg);}else updateSelection();
    received=true;motionLabel();if(reduced&&moving()){freeze();focusApp(nearest());}
  }
  function acceptIcons(encoded){for(const [pkg,value]of Object.entries(decode(encoded))){if(!value)continue;const img=new Image();img.onload=()=>{if(!apps.some(a=>a.pkg===pkg)){images.delete(pkg);return;}engine.clearTextures();paintIcons();draw();};img.src='data:image/png;base64,'+value;images.set(pkg,img);}}
  function suspend(){paused=true;if(sandMotion)sandMotion.cancel();freeze();if(sheetMotion.closing)closeModal(false);else sheetMotion.reset();}
  function resume(){paused=false;updateSelection(true);draw();}
  themeId=root.dataset.appearance==='star'?'star':root.dataset.appearance==='aurora'?'aurora':'saturn';setAppearance(themeId);engine=new window.SevenOrbitRenderer.Renderer($('.so-webgl'),$('.so-fallback-cards'),{apps:()=>apps,colors:()=>colors,dark:()=>dark.matches,drawIcon,theme:themeId,onSelect:index=>{freeze();if(index===selected)focusApp(index,details);else focusApp(index);}});root.dataset.renderer=engine.kind;updateSelection(true);motionLabel();draw();
  new ResizeObserver(()=>{engine.resize();draw();}).observe(stage);
  dark.addEventListener('change',()=>{readColors();engine.setTheme(themeId);root.querySelectorAll('[data-appearance-choice]').forEach(b=>Kit.thumbnail(b.querySelector('canvas'),b.dataset.appearanceChoice,dark.matches));draw();});
  systemReduced.addEventListener('change',()=>{reduced=nativeReduced||systemReduced.matches;if(reduced&&sandMotion)sandMotion.cancel();freeze();focusApp(nearest());});document.addEventListener('visibilitychange',()=>{if(document.hidden)freeze();else draw();});
  $('.so-webgl').addEventListener('webglcontextlost',e=>{e.preventDefault();freeze();engine.forceFallback();root.dataset.renderer=engine.kind;draw();});
  window.SevenNativeHome={accept,icons:acceptIcons,select:encoded=>{const i=apps.findIndex(a=>a.pkg===decode(encoded).pkg);if(i>=0)focusApp(i);},themes,close:()=>closeModal(true),suspend,resume,getState:()=>({theme:themeId,selected,count:apps.length,moving:moving(),angle,modal:!$('.so-modal').hidden,sheetMoving:sheetMotion.moving,...engine.stats()})};
  post('ready');
})();
