package cn.sevendays.focus;

import android.app.*;
import android.app.admin.*;
import android.app.job.*;
import android.content.*;
import android.os.*;
import java.util.*;

final class PolicyOps {
    static ComponentName admin(Context c){return new ComponentName(c,AdminReceiver.class);}
    static DevicePolicyManager manager(Context c){return (DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);}
    static boolean owner(Context c){try{DevicePolicyManager d=manager(c);return d!=null&&d.isDeviceOwnerApp(c.getPackageName());}catch(RuntimeException e){Diagnostics.event(c,"设备管理状态读取失败",e);return false;}}
    static boolean enabled(Context c){return owner(c)&&Store.get(c).prefs.getBoolean("owner_enabled",false);}
    static String error="";
    static void sync(Context c,boolean monitorAlive) {try{syncInternal(c,monitorAlive);}catch(RuntimeException e){error="增强保护检查失败";Diagnostics.event(c,error,e);}}
    private static void syncInternal(Context c,boolean monitorAlive) {
        if(!enabled(c))return;
        Store s=Store.get(c);if(s.corrupt){error="规则异常，保留现有管理策略";return;}long now=s.now();boolean locked=s.book.anyLocked(now);
        DevicePolicyManager d=manager(c);ComponentName a=admin(c);
        Set<String> want=new HashSet<>(), protect=new HashSet<>();
        for(RuleBook.Rule r:s.book.rules.values()){
            if(s.book.blocked(r.pkg)||!monitorAlive)want.add(r.pkg);
            if(s.book.locked(r.pkg,now))protect.add(r.pkg);
        }
        if(locked)protect.add(c.getPackageName());
        Set<String> previous=new HashSet<>(s.prefs.getStringSet("suspended",Collections.emptySet()));
        Set<String> oldProtect=new HashSet<>(s.prefs.getStringSet("protected",Collections.emptySet()));
        // Write intent before system calls so recovery can undo a partially applied operation.
        Set<String> journal=new HashSet<>(previous);journal.addAll(want);
        Set<String> journalProtect=new HashSet<>(oldProtect);journalProtect.addAll(protect);
        s.prefs.edit().putStringSet("suspended",journal).putStringSet("protected",journalProtect).commit();
        try {
            Set<String> release=new HashSet<>(previous);release.removeAll(want);
            List<String> failures=new ArrayList<>();
            if(!release.isEmpty())failures.addAll(Arrays.asList(d.setPackagesSuspended(a,release.toArray(new String[0]),false)));
            if(!want.isEmpty())failures.addAll(Arrays.asList(d.setPackagesSuspended(a,want.toArray(new String[0]),true)));
            for(String p:journalProtect)d.setUninstallBlocked(a,p,protect.contains(p));
            if(Build.VERSION.SDK_INT>=30)d.setUserControlDisabledPackages(a,new ArrayList<>(protect));
            if(locked){
                if(Build.VERSION.SDK_INT>=30)d.setAutoTimeEnabled(a,true);
                else d.setAutoTimeRequired(a,true);
                d.addUserRestriction(a,UserManager.DISALLOW_CONFIG_DATE_TIME);
            }else{
                d.clearUserRestriction(a,UserManager.DISALLOW_CONFIG_DATE_TIME);
                if(Build.VERSION.SDK_INT<30)d.setAutoTimeRequired(a,false);
            }
            if(!failures.isEmpty())throw new IllegalStateException("系统未能挂起/恢复部分应用："+String.join(", ",failures));
            s.prefs.edit().putStringSet("suspended",want).putStringSet("protected",protect).commit();
            error="";
        }catch(RuntimeException e){error="增强保护未完整生效："+e.getMessage();}
    }
    static void leave(Context c) {
        Store s=Store.get(c);if(s.corrupt)throw new IllegalStateException("规则异常，无法确认锁定状态");if(s.book.anyLocked(s.now()))throw new IllegalStateException("仍有规则在七天锁定期内");
        DevicePolicyManager d=manager(c);ComponentName a=admin(c);
        Set<String> suspended=s.prefs.getStringSet("suspended",Collections.emptySet());
        if(!suspended.isEmpty()&&d.setPackagesSuspended(a,suspended.toArray(new String[0]),false).length>0)throw new IllegalStateException("部分应用尚未恢复，暂不退出管理");
        for(String p:s.prefs.getStringSet("protected",Collections.emptySet()))d.setUninstallBlocked(a,p,false);
        if(Build.VERSION.SDK_INT>=30)d.setUserControlDisabledPackages(a,Collections.emptyList());
        d.clearUserRestriction(a,UserManager.DISALLOW_CONFIG_DATE_TIME);
        if(Build.VERSION.SDK_INT<30)d.setAutoTimeRequired(a,false);
        // Testing/self-managed device only; no wipe API is present anywhere in this project.
        d.clearDeviceOwnerApp(c.getPackageName());
        s.prefs.edit().putBoolean("owner_enabled",false).remove("suspended").remove("protected").commit();
        error="";
    }
    static void schedule(Context c) {try{scheduleInternal(c);}catch(RuntimeException e){Diagnostics.event(c,"系统定时检查安排失败",e);}}
    private static void scheduleInternal(Context c) {
        Store s=Store.get(c);if(s.corrupt)return;long now=s.now();
        JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(s.book.rules.isEmpty()){j.cancel(7001);PendingIntent old=PendingIntent.getBroadcast(c,7002,new Intent(c,WakeReceiver.class).setAction("cn.sevendays.focus.RECHECK"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_NO_CREATE);if(old!=null)((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(old);return;}
        if(j.getPendingJob(7001)==null)j.schedule(new JobInfo.Builder(7001,new ComponentName(c,PolicyJob.class)).setPeriodic(15*60*1000L).setPersisted(true).build());
        long next=s.book.nextMidnight(now);for(RuleBook.Rule r:s.book.rules.values())if(r.lockedUntil>now)next=Math.min(next,r.lockedUntil);
        PendingIntent pi=PendingIntent.getBroadcast(c,7002,new Intent(c,WakeReceiver.class).setAction("cn.sevendays.focus.RECHECK"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+Math.max(1000,next-now),pi);
    }
}
