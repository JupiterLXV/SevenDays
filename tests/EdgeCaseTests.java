package cn.sevendays.focus;
import java.util.*;
public class EdgeCaseTests {
 static int count;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
 static WindowPolicy.Window win(String p,int l,int t,int r,int b,int z,boolean safe){return new WindowPolicy.Window(p,new WindowPolicy.Box(l,t,r,b),z,safe);}
 static long area(WindowPolicy.Result r){long a=0;for(WindowPolicy.Region x:r.blocked)a+=(x.box.right-x.box.left)*(x.box.bottom-x.box.top);return a;}
 public static void main(String[] args){Set<String> blocked=new HashSet<>(Arrays.asList("limited"));
  WindowPolicy.Result r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,100,100,0,false)),blocked);check(area(r)==10000,"fullscreen mask");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,100,50,0,false),win("settings",0,50,100,100,1,true)),blocked);check(area(r)==5000,"safe split does not exempt limited half");check(r.visible.contains("limited"),"split tracked");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",60,60,100,100,2,false),win("own",0,0,100,100,0,true)),blocked);check(area(r)==1600,"PiP above own app blocks only PiP");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,100,100,0,false),win("dialer",0,0,100,100,1,true)),blocked);check(area(r)==0,"fullscreen call unobstructed");check(!r.visible.contains("limited"),"fully covered app not timed");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,100,100,0,false),win("dialer",25,25,75,75,1,true)),blocked);check(area(r)==7500,"safe overlay removes only its bounds");check(r.blocked.size()==4,"rectangle subtraction has four nonoverlapping parts");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,100,100,0,false),win("other",0,0,100,50,2,false)),blocked);check(area(r)==5000,"upper unrelated window excluded");check(r.visible.size()==2,"two visible apps independently tracked");
  r=WindowPolicy.evaluate(Arrays.asList(win("limited",0,0,0,100,0,false)),blocked);check(r.blocked.isEmpty(),"empty window ignored");
  check(WarningPolicy.threshold(30,300001)==0,"no early warning");check(WarningPolicy.threshold(30,300000)==5,"five minute boundary");check(WarningPolicy.threshold(30,60000)==1,"one minute boundary");check(WarningPolicy.threshold(30,0)==0,"no exhausted warning");check(WarningPolicy.threshold(5,300000)==0,"short quota skips five minute reminder");check(WarningPolicy.threshold(1,60000)==0,"one minute quota skips reminder");check(WarningPolicy.threshold(0,0)==0,"all day ban no warning");check(!WarningPolicy.key("a",1,5).equals(WarningPolicy.key("a",2,5)),"dedup distinguishes day");check(!WarningPolicy.key("a",1,5).equals(WarningPolicy.key("a",1,1)),"dedup distinguishes threshold");
  RuleBook book=new RuleBook();book.put("a","App",10,1000000);book.usage.put("a",400L);RuleBook copy=book.copy();copy.rules.get("a").minutes=20;copy.usage.put("a",700L);check(book.rules.get("a").minutes==10&&book.used("a")==400,"candidate mutation cannot change published model");book.replaceFrom(book);check(book.rules.size()==1,"self replacement retains rules");
  System.out.println("PASS "+count+" edge checks");
 }
}
