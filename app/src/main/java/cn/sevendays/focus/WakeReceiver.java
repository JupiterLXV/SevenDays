package cn.sevendays.focus;
import android.content.*;
public final class WakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        try{
            ProtectionService.restoreOnSystemEvent(c,i.getAction());
            Store s=Store.get(c);s.now();if(!s.corrupt)s.checkpointAsync();
            GuardService.refresh();PolicyOps.sync(c,GuardService.alive());PolicyOps.schedule(c);
        }catch(RuntimeException e){Diagnostics.event(c,"系统事件检查失败",e);}
    }
}
