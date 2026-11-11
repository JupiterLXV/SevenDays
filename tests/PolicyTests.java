import cn.sevendays.focus.RuleBook;
import java.time.*;
import java.util.*;
public final class PolicyTests {
    static int passed;
    static void ok(boolean v,String why){if(!v)throw new AssertionError(why);passed++;}
    static void rejects(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(IllegalStateException expected){passed++;}}
    public static void main(String[] args){
        RuleBook b=new RuleBook();b.zone="Asia/Shanghai";
        long start=ZonedDateTime.of(2026,10,10,12,0,0,0,ZoneId.of(b.zone)).toInstant().toEpochMilli();
        b.put("bili","B站",1,start);
        rejects(()->b.put("bili","B站",60,start+RuleBook.WEEK-1),"edit before end");
        rejects(()->b.remove("bili",start+RuleBook.WEEK-1),"delete before end");
        b.charge(Set.of("bili"),start,start+59999);ok(!b.blocked("bili"),"not prematurely blocked");
        b.charge(Set.of("bili"),start+59999,start+60000);ok(b.blocked("bili"),"blocks exact quota");
        b.rollover(b.nextMidnight(start));ok(b.used("bili")==0&&!b.blocked("bili"),"new day resets");
        b.put("zero","购物",0,start);ok(b.blocked("zero"),"zero minutes blocks immediately");
        b.put("bili","B站",2,start+RuleBook.WEEK);ok(b.locked("bili",start+RuleBook.WEEK+1),"editing after expiry relocks");
        long after=start+2*RuleBook.WEEK;b.charge(Set.of("bili"),after,after+30000);b.remove("bili",after+30000);b.put("bili","B站",1,after+30001);ok(b.used("bili")==30000,"delete readd preserves today usage");
        RuleBook split=new RuleBook();split.zone="Asia/Shanghai";split.put("a","A",30,start);split.put("b","B",30,start);long midnight=split.nextMidnight(start);
        split.charge(Set.of("a","b"),midnight-3000,midnight+2000);ok(split.used("a")==2000&&split.used("b")==2000,"midnight split and multi window");
        split.charge(Collections.emptySet(),midnight+2000,midnight+5000);ok(split.used("a")==2000,"screen off no charge");
        split.rollover(start);ok(split.used("a")==2000,"clock backwards no quota refill");
        ok(RuleBook.advance(start,100,1100,2,2,start,start+RuleBook.WEEK)==start+1000,"same boot forward clock ignored");
        ok(RuleBook.advance(start,100,1100,2,2,start,start-90000)==start+1000,"same boot backwards ignored");
        ok(RuleBook.advance(start,900000,100,2,3,start,start+60000)==start+60000,"reboot offline duration");
        ok(RuleBook.advance(start,900000,100,2,3,start,start-60000)==start,"reboot backwards clamped");
        ok(split.rules.containsKey("a"),"lock expiry does not delete rule");
        System.out.println("PASS: "+passed+" policy assertions");
    }
}
