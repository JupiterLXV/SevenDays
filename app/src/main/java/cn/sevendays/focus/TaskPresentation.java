package cn.sevendays.focus;
import android.app.*;
/** Controls only this app's task card; does not hide the launcher icon or grant keep-alive. */
final class TaskPresentation {
    static boolean excluded(android.content.Context c){return Store.get(c).prefs.getBoolean("exclude_task_card",true);}
    static int taskId(ActivityManager.RecentTaskInfo info){return android.os.Build.VERSION.SDK_INT>=29?info.taskId:info.id;}
    static boolean apply(Activity activity){try{ActivityManager manager=activity.getSystemService(ActivityManager.class);boolean found=false;for(ActivityManager.AppTask task:manager.getAppTasks())if(taskId(task.getTaskInfo())==activity.getTaskId()){task.setExcludeFromRecents(excluded(activity));found=true;}return found;}catch(RuntimeException e){Diagnostics.event(activity,"最近任务卡片设置失败",e);return false;}}
}
