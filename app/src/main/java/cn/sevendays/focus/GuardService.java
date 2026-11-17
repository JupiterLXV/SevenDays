package cn.sevendays.focus;
import android.accessibilityservice.AccessibilityService;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
import android.content.*;
import android.graphics.PixelFormat;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
/** Policy mutation stays on the main looper; accessibility window events are coalesced. */
public final class GuardService extends AccessibilityService {
    private static GuardService live;private static long heartbeat;private static String fault="";
    static boolean alive(){return live!=null&&fault.isEmpty()&&heartbeat>0&&SystemClock.elapsedRealtime()-heartbeat<5000;}
    static boolean hasFault(){return !fault.isEmpty();}
    static String status(Context c){return alive()?"已连接 · 计时与拦截正常":status(Diagnostics.authorization(c));}
    static String status(ProtectionRunState.Authorization authorization){
        if(alive())return "已连接 · 计时与拦截正常";
        if(!fault.isEmpty())return "保护异常 · 查看运行诊断";
        switch(authorization){
            case ENABLED:return "已授权 · 等待系统连接";
            case UNKNOWN:return "无障碍授权状态未确认";
            default:return "无障碍未开启";
        }
    }
    static void refresh(){if(live!=null)live.requestTick();}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Set<String> active=new HashSet<>();
    private final Set<String> safe=new HashSet<>(Arrays.asList("com.android.phone","com.android.server.telecom","com.android.incallui","com.android.settings"));
    private long last,policyTick;private volatile boolean connected;private boolean registered;private LinearLayout overlay;private WindowManager wm;
    private String lastPolicyKey="",barrierKey="";
    private final List<Barrier> barriers=new ArrayList<>();
    private final HomeTransition homeTransition=new HomeTransition();private HomeExitTrace homeExit;private String focusedPackage="";private boolean changeQueued;private long lastEventTick,lastMaintenance;
    private static final long WINDOW_FRESH_MS=1500;
    private WindowReadQueue<WindowReader.Snapshot> windowReads;private ExecutorService navigation;
    private volatile long sessionId;private long homeSequence;private boolean homeInFlight;private String homePackage="",windowFault="";
    private static final class Barrier {final String pkg;final boolean small;final LinearLayout view;final Button exit;WindowPolicy.Box box;Barrier(String p,boolean s,LinearLayout v,Button e,WindowPolicy.Box b){pkg=p;small=s;view=v;exit=e;box=b;}}private List<WindowPolicy.Window> lastWindows=Collections.emptyList();private long lastWindowsAt;
    private final Runnable pulse=new Runnable(){public void run(){if(!connected)return;safeTick();if(connected)handler.postDelayed(this,1000);}};
    private final Runnable changed=()->{changeQueued=false;lastEventTick=SystemClock.elapsedRealtime();safeTick();};
    private final Runnable homeTimeout=()->safeTick();
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){finishHomeExit(HomeExitTrace.Result.INTERRUPTED,SystemClock.elapsedRealtime());homeTransition.clear();invalidateWindows();hideOverlay();}safeTick();}};
    private void requestTick(){if(connected&&!changeQueued){changeQueued=true;long now=SystemClock.elapsedRealtime();handler.postDelayed(changed,Math.max(60,150-(now-lastEventTick)));}}
    @Override protected void onServiceConnected(){
        finishHomeExit(HomeExitTrace.Result.INTERRUPTED,SystemClock.elapsedRealtime());
        super.onServiceConnected();Ui.theme(this);handler.removeCallbacksAndMessages(null);if(windowReads!=null)windowReads.close();if(navigation!=null)navigation.shutdownNow();sessionId++;changeQueued=false;homeTransition.clear();lastWindows=Collections.emptyList();lastWindowsAt=0;homeInFlight=false;windowFault="";connected=true;live=this;heartbeat=0;fault="";last=0;active.clear();wm=(WindowManager)getSystemService(WINDOW_SERVICE);safe.add(getPackageName());
        try{TelecomManager t=(TelecomManager)getSystemService(TELECOM_SERVICE);if(t!=null){String dialer=t.getDefaultDialerPackage();if(dialer!=null)safe.add(dialer);}}catch(RuntimeException e){Diagnostics.event(this,"通话应用识别不可用",e);}
        try{ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);if(home!=null){homePackage=home.activityInfo.packageName;safe.add(homePackage);}}catch(RuntimeException e){Diagnostics.event(this,"桌面识别不可用",e);}
        WindowReader reader=new WindowReader(this,safe);
        windowReads=new WindowReadQueue<>(Executors.newSingleThreadExecutor(r->new Thread(r,"SevenDays-windows")),handler::post,reader::read,this::acceptWindows);
        // HOME must not queue behind a target app that is slow to answer an accessibility query.
        navigation=Executors.newSingleThreadExecutor(r->new Thread(r,"SevenDays-home"));
        if(!registered)try{IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,f);registered=true;}catch(RuntimeException e){Diagnostics.event(this,"屏幕状态监听不可用，保留周期检查",e);}
        Diagnostics.connected(this);handler.post(pulse);PolicyOps.schedule(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // A desktop event also handles the system HOME gesture, without waiting for a slow root query.
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!homePackage.isEmpty()&&homePackage.contentEquals(event.getPackageName()==null?"":event.getPackageName())){
            finishHomeExit(HomeExitTrace.Result.HOME,SystemClock.elapsedRealtime());
            homeTransition.clear();handler.removeCallbacks(homeTimeout);invalidateWindows();hideOverlay();
        }
        requestTick();
    }
    private void invalidateWindows(){if(windowReads!=null)windowReads.invalidate();lastWindows=Collections.emptyList();lastWindowsAt=0;focusedPackage="";active.clear();last=0;}
    private void acceptWindows(WindowReader.Snapshot snapshot,RuntimeException error){
        if(!connected||live!=this)return;
        if(error!=null){windowFault="窗口识别失败";invalidateWindows();hideOverlay();Diagnostics.event(this,"后台窗口识别失败，下次周期重试",error);}
        else{windowFault="";Diagnostics.windowReadMillis=snapshot.duration;Diagnostics.maxWindowReadMillis=Math.max(Diagnostics.maxWindowReadMillis,snapshot.duration);lastWindows=snapshot.windows;lastWindowsAt=snapshot.capturedAt;focusedPackage=snapshot.focused;}
        safeTick(false);
    }
    private List<WindowPolicy.Window> visible(){return SystemClock.elapsedRealtime()-lastWindowsAt<=WINDOW_FRESH_MS?lastWindows:Collections.emptyList();}
    private void safeTick(){safeTick(true);}
    private void safeTick(boolean readWindows){if(!connected||live!=this)return;try{if(readWindows&&windowReads!=null)windowReads.request();tick();String nextFault=windowFault.isEmpty()?Store.get(this).writeError:windowFault;if(!fault.isEmpty()&&nextFault.isEmpty())Diagnostics.event(this,"保护检查已恢复",null);fault=nextFault;long elapsed=SystemClock.elapsedRealtime();heartbeat=fault.isEmpty()&&lastWindowsAt>0&&elapsed-lastWindowsAt<5000?elapsed:0;if(heartbeat>0)Diagnostics.healthy(this);}catch(RuntimeException e){fault="检查失败";heartbeat=0;active.clear();last=0;hideOverlay();Diagnostics.event(this,"保护检查失败，下次周期重试",e);}}
    private void tick(){
        long transitionNow=SystemClock.elapsedRealtime();
        // Every entry path handles expiry, including a pulse racing the timeout callback.
        if(homeTransition.expired(transitionNow)){finishHomeExit(HomeExitTrace.Result.TIMEOUT,transitionNow);homeTransition.clear();invalidateWindows();if(windowReads!=null)windowReads.request();}
        Store s=Store.get(this);if(s.corrupt)throw new IllegalStateException("规则数据损坏");long now=s.now();
        boolean charged=last>0&&now>last&&!active.isEmpty();if(charged)s.book.charge(active,last,now);last=now;
        Set<String> exhausted=new HashSet<>();for(String p:s.book.rules.keySet())if(s.book.blocked(p))exhausted.add(p);
        WindowPolicy.Result windows=WindowPolicy.evaluate(visible(),exhausted);
        boolean wasLeaving=!homeTransition.source().isEmpty();
        boolean leaving=homeTransition.waiting(transitionNow,focusedPackage);
        if(wasLeaving&&!leaving)finishHomeExit(homePackage.equals(focusedPackage)?HomeExitTrace.Result.HOME:HomeExitTrace.Result.OTHER_WINDOW,transitionNow);
        if(leaving){String source=homeTransition.source();windows.blocked.removeIf(region->region.pkg.equals(source));}else handler.removeCallbacks(homeTimeout);
        showRegions(windows.blocked);
        active=new HashSet<>();for(String p:windows.visible)if(s.book.rules.containsKey(p)&&!s.book.blocked(p))active.add(p);s.checkpointUsage(charged);if(leaving||homeInFlight)return;long maintenance=SystemClock.elapsedRealtime();if(maintenance-lastMaintenance>=1000){lastMaintenance=maintenance;QuotaWarnings.update(this,s.book);}
        StringBuilder key=new StringBuilder();for(RuleBook.Rule r:s.book.rules.values())key.append(r.pkg).append(s.book.blocked(r.pkg)).append(s.book.locked(r.pkg,now));long elapsed=SystemClock.elapsedRealtime();if(!key.toString().equals(lastPolicyKey)||elapsed-policyTick>30000){PolicyOps.sync(this,s.writeError.isEmpty());lastPolicyKey=key.toString();policyTick=elapsed;}
    }
    private WindowManager.LayoutParams params(WindowPolicy.Box b){WindowManager.LayoutParams lp=new WindowManager.LayoutParams(b.right-b.left,b.bottom-b.top,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=b.left;lp.y=b.top;lp.windowAnimations=0;if(Build.VERSION.SDK_INT>=30)lp.setFitInsetsTypes(0);return lp;}
    private void showRegions(List<WindowPolicy.Region> regions){
        String key=regions.toString();if(key.equals(barrierKey))return;
        try{for(int i=0;i<regions.size();i++){WindowPolicy.Region region=regions.get(i);WindowPolicy.Box b=region.box;boolean small=b.right-b.left<Ui.dp(this,260)||b.bottom-b.top<Ui.dp(this,300);
            Barrier current=i<barriers.size()?barriers.get(i):null;
            if(current!=null&&current.pkg.equals(region.pkg)&&current.small==small){if(!current.box.toString().equals(b.toString())){wm.updateViewLayout(current.view,params(b));current.box=b;}continue;}
            if(current!=null){removeBarrier(current);barriers.remove(i);}
            Barrier next=createBarrier(region,small);wm.addView(next.view,params(b));barriers.add(i,next);
        }
        while(barriers.size()>regions.size())removeBarrier(barriers.remove(barriers.size()-1));
        overlay=barriers.isEmpty()?null:barriers.get(0).view;barrierKey=key;
        }catch(RuntimeException e){hideOverlay();throw e;}
    }
    private Barrier createBarrier(WindowPolicy.Region region,boolean small){
        RuleBook.Rule r=Store.get(this).book.rules.get(region.pkg);LinearLayout view=Ui.column(this);view.setGravity(Gravity.CENTER);Ui.pad(view,small?8:24,small?8:28);view.setBackgroundColor(Ui.BG);
        TextView title=Ui.bold(this,r!=null&&r.minutes==0?"全天禁用":"今日额度已用完",small?13:27);title.setGravity(Gravity.CENTER);view.addView(title);
        if(!small){Ui.space(view,18);TextView body=Ui.text(this,(r==null?"":r.name+"\n")+(r!=null&&r.minutes==0?"全天禁用规则生效中。":"每日 00:00 恢复额度。"),16,Ui.MUTED);body.setGravity(Gravity.CENTER);view.addView(body);Ui.space(view,24);}else Ui.space(view,6);
        Button home=Ui.button(this,homeInFlight?"正在返回…":small?"回桌面":"回到桌面",true,()->returnHome(region.pkg));home.setEnabled(!homeInFlight);if(small){home.setTextSize(11);Ui.pad(home,6,4);}view.addView(home);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(view);LinearLayout host=Ui.column(this);host.setBackgroundColor(Ui.BG);host.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return new Barrier(region.pkg,small,host,home,region.box);
    }
    private void returnHome(String pkg){
        long pressedAt=SystemClock.elapsedRealtime();
        if(!connected||homeInFlight||!homeTransition.begin(pkg,pressedAt))return;
        homeInFlight=true;long session=sessionId,sequence=++homeSequence;
        HomeExitTrace trace=new HomeExitTrace(pressedAt,System.currentTimeMillis());
        homeExit=trace;Diagnostics.homeExit=trace;
        // Visual dismissal is immediate. Old asynchronous snapshots can no longer put the mask back.
        boolean removed=hideOverlay();trace.overlayRemoved(SystemClock.elapsedRealtime(),removed);
        invalidateWindows();handler.removeCallbacks(homeTimeout);handler.postDelayed(homeTimeout,HomeTransition.MAX_MS);
        navigation.execute(()->{
            if(!connected||session!=sessionId)return;
            long at=SystemClock.elapsedRealtime();boolean accepted=false;RuntimeException failure=null;
            try{accepted=performGlobalAction(GLOBAL_ACTION_HOME);}catch(RuntimeException e){failure=e;}
            final boolean ok=accepted;final RuntimeException error=failure;final long duration=SystemClock.elapsedRealtime()-at;
            handler.post(()->{
                if(!connected||session!=sessionId||sequence!=homeSequence)return;
                homeInFlight=false;trace.requestFinished(at,duration,ok);
                if(!ok&&!homeTransition.source().isEmpty()){homeTransition.clear();handler.removeCallbacks(homeTimeout);Diagnostics.event(this,"系统未接受返回桌面操作，恢复拦截",error);Toast.makeText(this,"请使用系统主页手势返回桌面",Toast.LENGTH_SHORT).show();}
                // Re-enable a restored barrier in place; another exhausted pane stays blocked.
                for(Barrier b:barriers){b.exit.setEnabled(true);b.exit.setText(b.small?"回桌面":"回到桌面");}requestTick();
            });
        });
    }
    private void finishHomeExit(HomeExitTrace.Result result,long at){if(homeExit!=null)homeExit.finish(result,at);}
    private boolean removeBarrier(Barrier b){try{b.view.setVisibility(View.GONE);wm.removeView(b.view);return true;}catch(RuntimeException e){Diagnostics.event(this,"拦截窗口移除失败",e);return false;}}
    private boolean hideOverlay(){boolean ok=true;for(Barrier b:barriers)ok=removeBarrier(b)&&ok;barriers.clear();overlay=null;barrierKey="";return ok;}
    @Override public void onTaskRemoved(Intent intent){Diagnostics.taskRemoved(this,"无障碍服务收到任务卡片移除");requestTick();super.onTaskRemoved(intent);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);Ui.theme(this);invalidateWindows();hideOverlay();requestTick();}
    @Override public void onInterrupt(){Diagnostics.event(this,"系统请求中断无障碍反馈，继续检查保护状态",null);requestTick();}
    private void disconnect(String reason){
        finishHomeExit(HomeExitTrace.Result.INTERRUPTED,SystemClock.elapsedRealtime());
        if(!connected)return;connected=false;sessionId++;changeQueued=false;homeTransition.clear();handler.removeCallbacksAndMessages(null);if(windowReads!=null){windowReads.close();windowReads=null;}if(navigation!=null){navigation.shutdownNow();navigation=null;}hideOverlay();if(registered){try{unregisterReceiver(screen);}catch(RuntimeException e){Diagnostics.event(this,"屏幕监听清理失败",e);}registered=false;}
        try{Store s=Store.get(this);long now=s.now();if(last>0)s.book.charge(active,last,now);if(!s.corrupt)s.checkpointAsync();}catch(RuntimeException e){Diagnostics.event(this,"断开时保存失败",e);}
        active.clear();last=0;if(live==this){live=null;heartbeat=0;fault="";}Diagnostics.interrupted(this,reason);PolicyOps.sync(this,false);
    }
    @Override public boolean onUnbind(Intent intent){disconnect("系统已解绑无障碍服务");return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect("无障碍服务已销毁");super.onDestroy();}
}
