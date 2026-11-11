package cn.sevendays.focus;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import java.util.*;
/** Optional standard notifications; no extra screen overlay or quota extension. */
final class QuotaWarnings {
    static final String CHANNEL="quota_warning",PREFIX="quota:";
    private static boolean channelReady,cleanupNeeded=true;
    static boolean visible(Context c){try{NotificationManager m=c.getSystemService(NotificationManager.class);NotificationChannel ch=m.getNotificationChannel(CHANNEL);return m.areNotificationsEnabled()&&(ch==null||ch.getImportance()!=0);}catch(RuntimeException e){return false;}}
    static void update(Context c,RuleBook book){try{
        Store s=Store.get(c);boolean requested=s.prefs.getBoolean("quota_warning",false);
        // Disabled by default: after one cleanup, do not call the notification service every second.
        if(!requested&&!cleanupNeeded)return;
        NotificationManager m=c.getSystemService(NotificationManager.class);boolean enabled=requested&&visible(c);
        if(requested&&!channelReady){NotificationChannel channel=new NotificationChannel(CHANNEL,"额度即将用完",NotificationManager.IMPORTANCE_HIGH);channel.setDescription("剩余 5 分钟和 1 分钟时各提醒一次，可随时关闭提醒");m.createNotificationChannel(channel);channelReady=true;}
        for(StatusBarNotification n:m.getActiveNotifications())if(n.getTag()!=null&&n.getTag().startsWith(PREFIX)){
            String p=n.getTag().substring(PREFIX.length());RuleBook.Rule r=book.rules.get(p);if(!enabled||r==null||book.blocked(p)||n.getNotification().extras.getLong("quota_day",Long.MIN_VALUE)!=book.day||book.remaining(p)>n.getNotification().extras.getInt("quota_threshold",0)*60000L)m.cancel(n.getTag(),7011);
        }
        cleanupNeeded=requested;
        if(!enabled||s.corrupt)return;android.content.SharedPreferences prefs=c.getSharedPreferences("quota_warnings_v1",0);Set<String> seen=new HashSet<>(prefs.getStringSet("seen",Collections.emptySet()));
        if(prefs.getLong("day",Long.MIN_VALUE)!=book.day)seen.clear();boolean changed=false;
        for(RuleBook.Rule r:book.rules.values()){
            long remaining=book.remaining(r.pkg);int threshold=WarningPolicy.threshold(r.minutes,remaining);String key=WarningPolicy.key(r.pkg,book.day,threshold);if(threshold==0||seen.contains(key))continue;
            Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("sevendays://rule/"+Uri.encode(r.pkg))).putExtra("rule_pkg",r.pkg).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open=PendingIntent.getActivity(c,7011,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            android.os.Bundle extras=new android.os.Bundle();extras.putLong("quota_day",book.day);extras.putInt("quota_threshold",threshold);
            m.notify(PREFIX+r.pkg,7011,new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_status).setContentTitle(r.name+" · 还剩不到 "+threshold+" 分钟").setContentText("额度用完后将拦截应用。").setContentIntent(open).setAutoCancel(true).setTimeoutAfter(remaining).setVisibility(Notification.VISIBILITY_PRIVATE).addExtras(extras).build());seen.add(key);
            if(threshold==1)seen.add(WarningPolicy.key(r.pkg,book.day,5));changed=true;
        }
        if(changed||prefs.getLong("day",Long.MIN_VALUE)!=book.day)prefs.edit().putLong("day",book.day).putStringSet("seen",seen).apply();
    }catch(RuntimeException e){Diagnostics.event(c,"额度提醒不可用，计时拦截继续",e);}}
}
