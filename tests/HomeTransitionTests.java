package cn.sevendays.focus;
public class HomeTransitionTests {
 static int count;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
 public static void main(String[] args){
  HomeTransition h=new HomeTransition();
  check(!h.waiting(0,"app"),"idle never exempts an app");
  check(h.begin("blocked",1000),"first HOME accepted");
  check(!h.begin("blocked",1001),"rapid repeated HOME ignored");
  check(h.waiting(1701,"blocked"),"slow OEM transition is not remasked at the old 700ms boundary");
  check(h.waiting(2599,""),"missing root has a bounded wait");
  check(!h.expired(2599),"a pending transition does not evict a valid sample early");
  check(h.expired(2600),"pulse and timeout can both detect expiry before using old windows");
  check(!h.waiting(2600,"blocked"),"failed navigation expires exactly at the hard deadline");
  check(h.source().isEmpty(),"expiry removes the source exemption");
  check(h.begin("blocked",3000),"later retry is possible");
  check(!h.waiting(3001,"launcher"),"focused desktop completes immediately");
  check(!h.waiting(3002,"blocked"),"reopening the exhausted app has no leftover grace");
  h.begin("blocked",4000);check(!h.waiting(4001,"other-blocked"),"another exhausted app is never exempted");
  h.begin("blocked",5000);h.clear();check(!h.waiting(5001,"blocked"),"desktop event or rejected request clears the transition");
  check(h.begin("blocked",6000),"new transition starts after explicit completion");
  h.clear();check(h.source().isEmpty(),"disconnect clears all transition identity");
  System.out.println("PASS "+count+" transition checks");
 }
}
