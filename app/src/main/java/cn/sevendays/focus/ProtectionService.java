package cn.sevendays.focus;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
/** Optional user-visible foreground service; never toggles accessibility settings. */
public final class ProtectionService extends Service {
    static boolean running;
    private static final int ID=7010;
    private static final long START_PENDING_MS=5000;
    private static long startPendingAt=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ProtectionRunState runState=new ProtectionRunState();
    private String previous="";

    static boolean requested(Context c){
        return c.getSharedPreferences("policy_v1",MODE_PRIVATE).getBoolean("persistent_notice",false);
    }
    static void startFromActivity(Activity activity){requestStart(activity,"应用界面");}
    static void restoreOnSystemEvent(Context c,String action){
        String source;
        if(Intent.ACTION_BOOT_COMPLETED.equals(action))source="开机";
        else if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))source="应用更新";
        else return;
        if(requested(c)&&Diagnostics.authorization(c)==ProtectionRunState.Authorization.ENABLED)requestStart(c,source);
    }
    private static void requestStart(Context c,String source){
        if(running||!requested(c))return;
        long now=SystemClock.elapsedRealtime();
        if(startPendingAt>=0&&now-startPendingAt<START_PENDING_MS)return;
        startPendingAt=now;
        try{c.startForegroundService(new Intent(c,ProtectionService.class).putExtra("start_source",source));}
        catch(RuntimeException e){startPendingAt=-1;Diagnostics.event(c,"持续保护通知未能启动（"+source+"）",e);}
    }
    static boolean notificationVisible(Context c){try{NotificationManager m=c.getSystemService(NotificationManager.class);NotificationChannel channel=m.getNotificationChannel("protection_status");return m.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);}catch(RuntimeException e){return false;}}
    private Notification notification(String state){Intent intent=new Intent(this,MainActivity.class).putExtra("open_device",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent open=PendingIntent.getActivity(this,ID,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"protection_status").setSmallIcon(R.drawable.ic_status).setContentTitle("SevenDays "+state).setContentText("点击检查保护状态与运行诊断").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).build();}
    private void createChannel(){NotificationManager m=getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel("protection_status","持续保护状态",NotificationManager.IMPORTANCE_LOW);channel.setDescription("显示计时拦截是否正常；不会发出声音");channel.setShowBadge(false);m.createNotificationChannel(channel);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startPendingAt=-1;
        try{
            if(intent==null&&!requested(this)){stopSelfResult(id);return START_NOT_STICKY;}
            createChannel();previous=GuardService.status(this);Notification n=notification(previous);
            if(Build.VERSION.SDK_INT>=34)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(ID,n);
            if(!requested(this)){stopSelfResult(id);return START_NOT_STICKY;}
            running=true;
            String source=intent==null?"系统恢复":intent.getStringExtra("start_source");
            Diagnostics.event(this,"持续保护通知已启动"+(source==null?"":"（"+source+"）"),null);
            handler.removeCallbacks(pulse);handler.post(pulse);return START_STICKY;
        }catch(RuntimeException e){
            running=false;Diagnostics.event(this,"系统拒绝持续保护通知",e);stopSelf();return START_NOT_STICKY;
        }
    }
    private final Runnable pulse=new Runnable(){public void run(){
        try{
            ProtectionRunState.Authorization authorization=Diagnostics.authorization(ProtectionService.this);
            boolean enabled=requested(ProtectionService.this);
            if(!runState.keepRunning(enabled,authorization,SystemClock.elapsedRealtime())){
                if(enabled)Diagnostics.event(ProtectionService.this,"无障碍权限连续关闭，持续通知已停止",null);
                stopSelf();return;
            }
            String state=GuardService.status(authorization);
            if(!state.equals(previous)){getSystemService(NotificationManager.class).notify(ID,notification(state));previous=state;}
        }catch(RuntimeException e){Diagnostics.event(ProtectionService.this,"持续通知刷新失败",e);}
        handler.postDelayed(this,5000);
    }};
    @Override public void onTaskRemoved(Intent intent){Diagnostics.taskRemoved(this,"持续保护服务收到任务卡片移除");GuardService.refresh();super.onTaskRemoved(intent);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);startPendingAt=-1;
        if(running)Diagnostics.event(this,"持续保护通知服务已结束",null);
        running=false;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
