package cn.sevendays.focus;
import java.util.*;
public final class InteractionTests {
    static int checks;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static void rejected(Runnable action){try{action.run();throw new AssertionError("invalid batch accepted");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    public static void main(String[] args){
        long now=1793358000000L;RuleBook b=new RuleBook();b.put("old","原应用",70,now);b.usage.put("old",1921000L);b.usage.put("new1",65000L);long until=b.rules.get("old").lockedUntil;
        rejected(()->BatchRules.applyNew(b,Arrays.asList(new BatchRules.Entry("new1","一",20),new BatchRules.Entry("old","原应用",30)),now+100));
        check(b.rules.size()==1&&!b.rules.containsKey("new1"),"conflicting batch is rejected before any mutation");
        rejected(()->BatchRules.applyNew(b,Arrays.asList(new BatchRules.Entry("new1","一",20),new BatchRules.Entry("new2","二",1441)),now+100));
        rejected(()->BatchRules.applyNew(b,Arrays.asList(new BatchRules.Entry("new1","一",20),new BatchRules.Entry("new1","重复",30)),now+100));
        check(b.rules.size()==1&&b.used("old")==1921000L&&b.used("new1")==65000L,"invalid batches preserve rules and all usage");
        BatchRules.applyNew(b,Arrays.asList(new BatchRules.Entry("new1","一",0),new BatchRules.Entry("new2","二",1440)),now+100);
        check(b.blocked("new1")&&b.rules.get("new2").minutes==1440,"both daily quota boundaries are accepted");
        check(b.rules.get("new1").lockedUntil==now+100+RuleBook.WEEK&&b.rules.get("new2").lockedUntil==b.rules.get("new1").lockedUntil,"batch receives a common exact seven day deadline");
        check(b.used("old")==1921000L&&b.used("new1")==65000L&&b.rules.get("old").lockedUntil==until,"successful batch retains prior usage and deadline");
        check(b.remainingWithQuota("old",30)==0&&b.remainingWithQuota("old",45)==779000L,
            "a lower proposed quota can exhaust today without changing the current rule");
        check(b.rules.get("old").minutes==70&&b.rules.get("old").lockedUntil==until,"preview leaves quota and seven-day lock untouched");
        RuleBook readded=new RuleBook();readded.put("p","P",60,now);long editable=now+RuleBook.WEEK;
        readded.rollover(editable);readded.usage.put("p",42*60000L);readded.remove("p",editable);
        long preview=readded.remainingWithQuota("p",30);
        BatchRules.applyNew(readded,List.of(new BatchRules.Entry("p","P",30)),editable);
        check(preview==0&&readded.remaining("p")==preview&&readded.blocked("p"),"preview matches a re-added rule with retained usage");
        readded.rollover(readded.nextMidnight(editable));
        check(readded.remainingWithQuota("p",30)==30*60000L&&readded.locked("p",readded.nextMidnight(editable)),"daily refill and rule unlock are independent");
        List<BatchRules.Entry> draft=Arrays.asList(new BatchRules.Entry("x","X",30));List<BatchRules.Entry> copied=BatchRules.copy(draft);copied.get(0).minutes=60;check(draft.get(0).minutes==30,"submission snapshot is independent of editable draft");
        float previous=-1;for(int n=0;n<=1440;n++){float p=BandScale.position(n);check(p>=previous&&p>=0&&p<=1,"time scale is monotonic and bounded");check(BandScale.minutes(p)==n,"all integer minutes round trip");previous=p;}
        check(BandScale.minutes(-2)==0&&BandScale.minutes(2)==1440&&BandScale.position(-1)==0&&BandScale.position(1441)==1,"scale clamps out of range gestures");
        List<String> actual=Arrays.asList("a","b","c");List<String> reconciled=DisplayOrder.reconcile(actual,Arrays.asList("c","missing","c","a"));check(reconciled.equals(Arrays.asList("c","a","b")),"display order removes missing and duplicate entries");
        check(DisplayOrder.move(actual,"a",2).equals(Arrays.asList("b","c","a"))&&actual.equals(Arrays.asList("a","b","c")),"display reorder does not mutate source collection");
        check(DisplayOrder.move(actual,"c",-1).equals(Arrays.asList("c","a","b"))&&DisplayOrder.move(actual,"missing",0).equals(actual),"reorder handles endpoints and stale packages");
        System.out.println("PASS "+checks+" interaction policy checks");
    }
}
