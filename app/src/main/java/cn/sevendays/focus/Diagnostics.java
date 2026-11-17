package cn.sevendays.focus;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.text.SimpleDateFormat;
import java.util.*;
/** Bounded local lifecycle diagnostics; never records screen text or target app history. */
final class Diagnostics {
    private static String lastKey="";private static long lastAt;
    static long windowReadMillis=-1,maxWindowReadMillis=-1;
    static HomeExitTrace homeExit;
    private static boolean connectionNeedsCheck;
    static int orbitReleases;
    private static long exitCacheAt=-1;
    private static String exitCache="";
    static ProtectionRunState.Authorization authorization(Context c){
        try{
            String value=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName ours=new ComponentName(c,GuardService.class);
            if(value!=null)for(String name:value.split(":"))if(ours.equals(ComponentName.unflattenFromString(name)))return ProtectionRunState.Authorization.ENABLED;
            return ProtectionRunState.Authorization.DISABLED;
        }catch(RuntimeException e){event(c,"授权状态读取失败",e);return ProtectionRunState.Authorization.UNKNOWN;}
    }
    static boolean granted(Context c){return authorization(c)==ProtectionRunState.Authorization.ENABLED;}
    static synchronized void event(Context c,String message,Throwable error){try{
        String detail=message;if(error!=null){detail+=" · "+error.getClass().getSimpleName();for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().startsWith("cn.sevendays.focus")){detail+=" @ "+frame.getFileName()+":"+frame.getLineNumber();break;}}
        long now=System.currentTimeMillis();if(detail.equals(lastKey)&&now-lastAt<30000)return;lastKey=detail;lastAt=now;
        SharedPreferences p=c.getSharedPreferences("diagnostics_v1",0);String history=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date(now))+"  "+detail+"\n"+p.getString("events","");String[] lines=history.split("\n");StringBuilder limited=new StringBuilder();for(int i=0;i<Math.min(40,lines.length);i++)limited.append(lines[i]).append('\n');p.edit().putString("events",limited.toString()).apply();
    }catch(RuntimeException ignored){}}
    static void taskRemoved(Context c,String message){try{c.getSharedPreferences("diagnostics_v1",0).edit().putLong("task_removed",System.currentTimeMillis()).apply();}catch(RuntimeException ignored){}event(c,message,null);}
    static void connected(Context c){connectionNeedsCheck=true;try{c.getSharedPreferences("diagnostics_v1",0).edit().putLong("connected",System.currentTimeMillis()).apply();}catch(RuntimeException ignored){}event(c,"无障碍服务已接上，等待保护检查",null);}
    static void healthy(Context c){try{
        SharedPreferences p=c.getSharedPreferences("diagnostics_v1",0);long now=System.currentTimeMillis(),last=p.getLong("healthy",0);
        if(connectionNeedsCheck){
            connectionNeedsCheck=false;SharedPreferences.Editor edit=p.edit().putLong("healthy",now);
            boolean recovered=last>0&&now>=last&&(now-last>15000||p.getLong("interrupted",0)>=last);
            if(recovered)edit.putLong("recovered",now).putLong("gap",now-last);
            edit.apply();
            if(recovered)event(c,"保护重新通过检查",null);
        }else if(now-last>10000)p.edit().putLong("healthy",now).apply();
    }catch(RuntimeException ignored){}}
    static void interrupted(Context c,String reason){try{c.getSharedPreferences("diagnostics_v1",0).edit().putLong("interrupted",System.currentTimeMillis()).apply();}catch(RuntimeException ignored){}event(c,reason,null);}
    private static String time(long t){return t<=0?"暂无记录":new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date(t));}
    static String timeline(Context c){try{SharedPreferences p=c.getSharedPreferences("diagnostics_v1",0);return "最近任务移除回调："+time(p.getLong("task_removed",0))+"\n最近连接："+time(p.getLong("connected",0))+"\n最近成功检查："+time(p.getLong("healthy",0))+"\n最近断开："+time(p.getLong("interrupted",0))+"\n最近恢复："+time(p.getLong("recovered",0))+"\n最近未确认保护间隔约："+Ui.duration(p.getLong("gap",0))+"\n间隔根据检查时间估算，不是准确漏计时长；不会补算未知用量。";}catch(RuntimeException e){return "运行时间记录暂时不可用；不影响规则本身。";}}
    static String advice(Context c){return advice(c,authorization(c));}
    private static String advice(Context c,ProtectionRunState.Authorization auth){
        Store s=Store.get(c);
        if(s.corrupt)return "规则读取异常：保留应用数据，复制诊断反馈；不要清除数据或重装。";
        if(!s.writeError.isEmpty())return s.writeError+"。";
        if(!PolicyOps.error.isEmpty())return PolicyOps.error;
        if(GuardService.hasFault())return "保护检查异常。请保留运行诊断；服务会继续检查。";
        if(GuardService.alive())return "计时与拦截已连接。";
        if(auth==ProtectionRunState.Authorization.UNKNOWN)return "暂时无法确认权限状态，可打开系统无障碍设置查看。";
        if(auth==ProtectionRunState.Authorization.DISABLED)return "请在系统设置开启 SevenDays 无障碍主开关。";
        return "授权仍开启，等待系统连接。若持续未恢复，请先复制诊断，再重新开启无障碍主开关。";
    }
    private static String events(Context c){try{return c.getSharedPreferences("diagnostics_v1",0).getString("events","暂无记录");}catch(RuntimeException e){return "事件记录暂时不可用";}}
    static String report(Context c,DeviceInfo device){
        ProtectionRunState.Authorization auth=authorization(c);
        StringBuilder b=new StringBuilder(device.report(c))
            .append("\n\n无障碍授权：").append(auth==ProtectionRunState.Authorization.ENABLED?"已开启":auth==ProtectionRunState.Authorization.DISABLED?"未开启":"暂未确认")
            .append("\n实际保护：").append(GuardService.status(auth))
            .append("\n最近任务卡片偏好：").append(TaskPresentation.excluded(c)?"不显示":"保留")
            .append("\n持续通知偏好：").append(ProtectionService.requested(c)?"已开启":"未开启")
            .append("\n前台状态服务：").append(ProtectionService.running?"运行中":"未运行")
            .append("\n系统通知：").append(ProtectionService.notificationVisible(c)?"允许显示":"未允许显示")
            .append("\n系统电池优化：").append(batteryState(c))
            .append("\n本次进程已运行：").append(Ui.duration(Math.max(0,SystemClock.elapsedRealtime()-android.os.Process.getStartElapsedRealtime())))
            .append("\n星环后台释放次数：").append(orbitReleases);
        if(Build.VERSION.SDK_INT>=30)b.append("\n\n系统记录的最近进程退出：\n").append(exitHistory(c));
        b.append("\n\n本次进程的操作耗时（不是帧率）：\n窗口查询：后台执行 · 最近 ").append(millis(windowReadMillis)).append(" · 最长 ").append(millis(maxWindowReadMillis));
        HomeExitTrace exit=homeExit;
        if(exit==null)b.append("\n返回桌面：暂无记录");
        else b.append("\n返回桌面（").append(time(exit.wallAt)).append("）：").append(exit.result.label)
            .append("\n按下 → 遮挡移除请求返回：").append(millis(exit.overlayMillis))
            .append(exit.overlayMillis>=0&&!exit.overlayRequestOk?" · 移除请求异常":"")
            .append("\n按下 → 发出返回请求：").append(millis(exit.requestDelayMillis))
            .append("\n系统返回请求耗时：").append(millis(exit.requestMillis))
            .append("\n按下 → 确认桌面窗口：").append(millis(exit.desktopMillis))
            .append("\n窗口确认不代表动画已完成。");
        return b.append("\n\n").append(timeline(c)).append("\n\n").append(advice(c,auth)).append("\n最近运行事件（最多 40 条）：\n").append(events(c)).toString();
    }
    private static String batteryState(Context c){
        try{
            PowerManager power=c.getSystemService(PowerManager.class);
            return power==null?"未知":power.isIgnoringBatteryOptimizations(c.getPackageName())?"已忽略":"未忽略";
        }catch(RuntimeException e){return "未知";}
    }
    private static String exitHistory(Context c){
        long now=SystemClock.elapsedRealtime();
        if(exitCacheAt>=0&&now-exitCacheAt<15000)return exitCache;
        StringBuilder out=new StringBuilder();
        try{
            ActivityManager am=c.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits=am.getHistoricalProcessExitReasons(c.getPackageName(),0,5);
            if(exits.isEmpty())out.append("暂无记录\n");
            for(ApplicationExitInfo x:exits){
                out.append(time(x.getTimestamp())).append(" · ").append(reason(x.getReason()))
                    .append(" · 状态 ").append(x.getStatus()).append(" · 进程级别 ").append(x.getImportance());
                if(x.getPss()>0)out.append(" · 最近 PSS ").append(x.getPss()/1024).append(" MiB");
                String detail=x.getDescription();
                if(detail!=null){detail=detail.replaceAll("\\s+"," ").trim();if(!detail.isEmpty())out.append("\n  系统详情：").append(detail,0,Math.min(detail.length(),160));}
                out.append('\n');
            }
        }catch(RuntimeException e){out.setLength(0);out.append("系统退出记录不可用\n");}
        exitCache=out.toString();exitCacheAt=now;return exitCache;
    }
    private static String millis(long value){return value<0?"暂无记录":value+" 毫秒";}
    private static String reason(int code){switch(code){case ApplicationExitInfo.REASON_SIGNALED:return "信号终止";case ApplicationExitInfo.REASON_LOW_MEMORY:return "内存不足";case ApplicationExitInfo.REASON_CRASH:return "Java 崩溃";case ApplicationExitInfo.REASON_CRASH_NATIVE:return "原生崩溃";case ApplicationExitInfo.REASON_ANR:return "无响应 ANR";case ApplicationExitInfo.REASON_PERMISSION_CHANGE:return "权限变更";case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return "资源使用过多";case ApplicationExitInfo.REASON_USER_REQUESTED:return Build.VERSION.SDK_INT<34?"用户请求或应用升级":"用户请求停止";case ApplicationExitInfo.REASON_USER_STOPPED:return "用户空间停止";case ApplicationExitInfo.REASON_DEPENDENCY_DIED:return "依赖进程退出";case ApplicationExitInfo.REASON_FREEZER:return "进程冻结管理";case ApplicationExitInfo.REASON_PACKAGE_UPDATED:return "应用升级";case ApplicationExitInfo.REASON_OTHER:return "其他系统原因（13）";default:return "原因代码 "+code;}}
}
