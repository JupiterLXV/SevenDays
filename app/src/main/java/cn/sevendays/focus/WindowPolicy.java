package cn.sevendays.focus;
import java.util.*;
/** Pure rectangular visible-window calculation, independent of Android UI. */
final class WindowPolicy {
    static final class Box {
        final int left,top,right,bottom;
        Box(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        boolean valid(){return right>left&&bottom>top;}
        @Override public String toString(){return left+","+top+","+right+","+bottom;}
        List<Box> subtract(Box cover){int l=Math.max(left,cover.left),t=Math.max(top,cover.top),r=Math.min(right,cover.right),b=Math.min(bottom,cover.bottom);if(l>=r||t>=b)return Collections.singletonList(this);List<Box> out=new ArrayList<>();for(Box x:new Box[]{new Box(left,top,right,t),new Box(left,b,right,bottom),new Box(left,t,l,b),new Box(r,t,right,b)})if(x.valid())out.add(x);return out;}
    }
    static final class Window {final String pkg;final Box box;final int layer;final boolean safe;Window(String p,Box b,int l,boolean s){pkg=p;box=b;layer=l;safe=s;}}
    static final class Region {final String pkg;final Box box;Region(String p,Box b){pkg=p;box=b;}@Override public String toString(){return pkg+":"+box;}}
    static final class Result {final Set<String> visible=new LinkedHashSet<>();final List<Region> blocked=new ArrayList<>();}
    static Result evaluate(List<Window> input,Set<String> exhausted){List<Window> sorted=new ArrayList<>(input);sorted.sort((a,b)->Integer.compare(b.layer,a.layer));List<Box> above=new ArrayList<>();Result out=new Result();for(Window w:sorted){if(!w.box.valid())continue;List<Box> parts=new ArrayList<>();parts.add(w.box);for(Box cover:above){List<Box> next=new ArrayList<>();for(Box b:parts)next.addAll(b.subtract(cover));parts=next;if(parts.isEmpty())break;}if(!parts.isEmpty()&&!w.safe){out.visible.add(w.pkg);if(exhausted.contains(w.pkg))for(Box part:parts)out.blocked.add(new Region(w.pkg,part));}above.add(w.box);}return out;}
}
