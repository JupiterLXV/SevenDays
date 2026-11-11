package cn.sevendays.focus;
import android.app.job.*;
public final class PolicyJob extends JobService {
    @Override public boolean onStartJob(JobParameters p){try{Store s=Store.get(this);s.now();s.checkpoint(true);PolicyOps.sync(this,GuardService.alive());PolicyOps.schedule(this);}catch(RuntimeException e){Diagnostics.event(this,"后台检查失败",e);}return false;}
    @Override public boolean onStopJob(JobParameters p){return true;}
}
