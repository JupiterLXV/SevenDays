package cn.sevendays.focus;
import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** Model access stays on the main looper. Disk writes have one ordered queue. */
final class Store {
    private static Store instance;
    static synchronized Store get(Context c){if(instance==null)instance=new Store(c.getApplicationContext());return instance;}
    final RuleBook book=new RuleBook();final SharedPreferences prefs;final PolicyDatabase database;
    private final Context context;private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final SnapshotCadence cadence=new SnapshotCadence();
    private long logical,elapsed,wall;private int boot;boolean corrupt;
    volatile String writeError="";private Future<?> pending;
    Store(Context c){context=c;prefs=c.getSharedPreferences("policy_v1",0);database=new PolicyDatabase(c);
        long real=System.currentTimeMillis();logical=wall=real;elapsed=SystemClock.elapsedRealtime();boot=bootCount();
        boolean existed=c.getDatabasePath("policy_v2.db").exists();
        try{String raw=database.read();
            if(raw!=null){JSONObject all=new JSONObject(raw);if(all.getInt("schema")!=2)throw new JSONException("未知规则格式");readClock(all);book.replaceFrom(decode(all.getJSONObject("book")));}
            else {if(existed)throw new JSONException("规则快照缺失");if(prefs.contains("book")){
                for(String key:new String[]{"logical","elapsed","wall","boot"})if(!prefs.contains(key))throw new JSONException("旧数据缺少时间记录");
                logical=prefs.getLong("logical",0);elapsed=prefs.getLong("elapsed",0);wall=prefs.getLong("wall",0);boot=prefs.getInt("boot",-1);book.replaceFrom(decode(new JSONObject(prefs.getString("book",""))));}
                database.write(encode(book));}
        }catch(Exception e){corrupt=true;Diagnostics.event(c,"规则读取失败，保留原数据",e);}
    }
    private void readClock(JSONObject o)throws JSONException{logical=o.getLong("logical");elapsed=o.getLong("elapsed");wall=o.getLong("wall");boot=o.getInt("boot");}
    private int bootCount(){return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    long now(){long e=SystemClock.elapsedRealtime(),w=System.currentTimeMillis();int b=bootCount();logical=RuleBook.advance(logical,elapsed,e,boot,b,wall,w);elapsed=e;wall=w;boot=b;book.rollover(logical);return logical;}
    static RuleBook decode(JSONObject o)throws JSONException{
        RuleBook b=new RuleBook();b.zone=o.getString("zone");java.time.ZoneId.of(b.zone);b.day=o.getLong("day");JSONArray rules=o.getJSONArray("rules");
        for(int i=0;i<rules.length();i++){JSONObject r=rules.getJSONObject(i);String p=r.getString("pkg");int m=r.getInt("minutes");long until=r.getLong("until");if(p.isEmpty()||m<0||m>1440||until<0||b.rules.containsKey(p))throw new JSONException("无效规则");b.rules.put(p,new RuleBook.Rule(p,r.getString("name"),m,until));}
        JSONObject u=o.getJSONObject("usage");Iterator<String> keys=u.keys();while(keys.hasNext()){String p=keys.next();long value=u.getLong(p);if(value<0||value>86400000L)throw new JSONException("无效用量");b.usage.put(p,value);}return b;
    }
    private String encode(RuleBook b){try{JSONObject o=new JSONObject();o.put("zone",b.zone);o.put("day",b.day);JSONArray rules=new JSONArray();for(RuleBook.Rule r:b.rules.values())rules.put(new JSONObject().put("pkg",r.pkg).put("name",r.name).put("minutes",r.minutes).put("until",r.lockedUntil));o.put("rules",rules);JSONObject u=new JSONObject();for(Map.Entry<String,Long> e:b.usage.entrySet())u.put(e.getKey(),e.getValue());o.put("usage",u);
        return new JSONObject().put("schema",2).put("book",o).put("logical",logical).put("elapsed",elapsed).put("wall",wall).put("boot",boot).toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    private Future<?> enqueue(String payload){return disk.submit(()->{try{database.write(payload);writeError="";}catch(RuntimeException e){writeError="规则保存失败，请检查存储空间并查看诊断";Diagnostics.event(context,writeError,e);throw e;}});}
    private void await(Future<?> job){try{job.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("规则保存等待被中断",e);}catch(ExecutionException e){throw new IllegalStateException(writeError,e.getCause());}}
    private void writable(){if(corrupt)throw new IllegalStateException("规则数据异常，已停止写入并保留原文件");}
    void mutate(Consumer<RuleBook> action){writable();now();RuleBook next=book.copy();action.accept(next);String data=encode(next);await(enqueue(data));book.replaceFrom(next);}
    void save(){writable();await(enqueue(encode(book)));}
    void checkpointUsage(boolean charged){
        writable();long t=SystemClock.elapsedRealtime();
        if(cadence.due(t,book.day,charged,pending!=null&&!pending.isDone(),!writeError.isEmpty())){
            pending=enqueue(encode(book));cadence.enqueued(t,book.day);
        }
    }
    /** Lifecycle flush: capture on the model thread, commit in queue order without blocking HOME. */
    void checkpointAsync(){writable();pending=enqueue(encode(book));cadence.enqueued(SystemClock.elapsedRealtime(),book.day);}
    void checkpoint(boolean force){if(force){save();cadence.enqueued(SystemClock.elapsedRealtime(),book.day);}else checkpointUsage(false);}
    void close(){disk.shutdown();try{disk.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}database.close();}
}
