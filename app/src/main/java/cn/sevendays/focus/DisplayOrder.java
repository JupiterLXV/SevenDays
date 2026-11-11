package cn.sevendays.focus;
import java.util.*;
/** Display-only ordering. No rule values or timestamps are modified. */
final class DisplayOrder {
    static List<String> reconcile(Collection<String> actual,Collection<String> saved){LinkedHashSet<String> out=new LinkedHashSet<>();Set<String> valid=new HashSet<>(actual);for(String p:saved)if(valid.contains(p))out.add(p);out.addAll(actual);return new ArrayList<>(out);}
    static List<String> move(List<String> source,String pkg,int index){List<String> out=new ArrayList<>(source);if(!out.remove(pkg))return out;out.add(Math.max(0,Math.min(out.size(),index)),pkg);return out;}
}
