package cn.sevendays.focus;
import java.util.*;
/** UI submission helper: validate the complete new-rule batch before touching the model. */
final class BatchRules {
    static final class Entry {
        final String pkg,name;int minutes;
        Entry(String pkg,String name,int minutes){this.pkg=pkg;this.name=name;this.minutes=minutes;}
        Entry copy(){return new Entry(pkg,name,minutes);}
    }
    static List<Entry> copy(List<Entry> entries){List<Entry> out=new ArrayList<>();for(Entry e:entries)out.add(e.copy());return out;}
    static void applyNew(RuleBook book,List<Entry> entries,long now){
        if(entries==null||entries.isEmpty())throw new IllegalArgumentException("尚未选择应用");Set<String> seen=new HashSet<>();
        for(Entry e:entries){if(e==null||e.pkg==null||e.pkg.isEmpty()||e.name==null||e.minutes<0||e.minutes>1440)throw new IllegalArgumentException("请输入 0–1440 的整数分钟");if(!seen.add(e.pkg))throw new IllegalArgumentException("同一应用不能重复添加");if(book.rules.containsKey(e.pkg))throw new IllegalStateException(e.name+" 已有规则，请重新选择");}
        for(Entry e:entries)book.put(e.pkg,e.name,e.minutes,now);
    }
}
