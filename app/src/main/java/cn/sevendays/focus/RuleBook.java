package cn.sevendays.focus;

import java.time.*;
import java.util.*;

/** Pure policy model; every write passes here, independently of UI state. */
public final class RuleBook {
    public static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    public static final class Rule {
        public String pkg, name;
        public int minutes;
        public long lockedUntil;
        public Rule(String p, String n, int m, long until) { pkg=p; name=n; minutes=m; lockedUntil=until; }
    }
    public final Map<String,Rule> rules = new LinkedHashMap<>();
    public final Map<String,Long> usage = new HashMap<>();
    public String zone = ZoneId.systemDefault().getId();
    public long day = Long.MIN_VALUE;
    public RuleBook copy(){RuleBook other=new RuleBook();other.replaceFrom(this);return other;}
    public void replaceFrom(RuleBook other){if(other==this)return;rules.clear();for(Rule r:other.rules.values())rules.put(r.pkg,new Rule(r.pkg,r.name,r.minutes,r.lockedUntil));usage.clear();usage.putAll(other.usage);zone=other.zone;day=other.day;}
    public long dayOf(long t) { return Instant.ofEpochMilli(t).atZone(ZoneId.of(zone)).toLocalDate().toEpochDay(); }
    public long nextMidnight(long t) { return Instant.ofEpochMilli(t).atZone(ZoneId.of(zone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli(); }
    public void rollover(long now) { long d=dayOf(now); if(d>day) { day=d; usage.clear(); } }
    public boolean locked(String pkg,long now) { Rule r=rules.get(pkg); return r!=null && now<r.lockedUntil; }
    public boolean anyLocked(long now) { for(Rule r:rules.values()) if(now<r.lockedUntil)return true; return false; }
    public void put(String pkg,String name,int minutes,long now) {
        if(locked(pkg,now))throw new IllegalStateException("这条规则仍在七天锁定期内");
        if(pkg==null||pkg.isEmpty()||minutes<0||minutes>1440)throw new IllegalArgumentException("请输入 0–1440 分钟");
        rollover(now);
        rules.put(pkg,new Rule(pkg,name,minutes,now+WEEK));
    }
    public void remove(String pkg,long now) {
        if(locked(pkg,now))throw new IllegalStateException("七天内不能删除规则");
        rules.remove(pkg); // Keep today's usage: delete/re-add must not refill quota.
    }
    public long used(String pkg) { return usage.getOrDefault(pkg,0L); }
    public long remaining(String pkg) { Rule r=rules.get(pkg); return r==null?Long.MAX_VALUE:Math.max(0,r.minutes*60000L-used(pkg)); }
    public boolean blocked(String pkg) { return rules.containsKey(pkg)&&remaining(pkg)==0; }
    public void charge(Set<String> packages,long from,long until) {
        if(until<=from)return;
        // Only the latest day's segment is relevant; split at local midnight.
        rollover(until);
        long start=Instant.ofEpochMilli(until).atZone(ZoneId.of(zone)).toLocalDate().atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli();
        long delta=until-Math.max(from,start);
        for(String p:packages)if(rules.containsKey(p))usage.put(p,Math.min(86400000L,used(p)+delta));
    }
    public static long advance(long logical,long previousElapsed,long elapsed,int previousBoot,int boot,long previousWall,long wall) {
        if(previousBoot==boot && elapsed>=previousElapsed)return logical+(elapsed-previousElapsed);
        // No trusted network clock in ordinary mode: powered-off interval relies on wall clock.
        return logical+Math.max(0,wall-previousWall);
    }
}
