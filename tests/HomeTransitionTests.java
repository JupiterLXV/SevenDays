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
  HomeExitTrace trace=new HomeExitTrace(1000,2000);
  trace.overlayRemoved(1007,true);
  trace.requestFinished(1012,21,true);
  check(trace.result==HomeExitTrace.Result.WAITING&&trace.desktopMillis<0,"accepted system request is not proof of a visible launcher");
  trace.finish(HomeExitTrace.Result.HOME,1230);
  trace.finish(HomeExitTrace.Result.TIMEOUT,2600);
  check(trace.result==HomeExitTrace.Result.HOME&&trace.desktopMillis==230&&trace.overlayMillis==7&&trace.requestDelayMillis==12&&trace.requestMillis==21,"reported stages measure different parts of a completed exit");
  HomeExitTrace rejected=new HomeExitTrace(4000,6000);
  rejected.requestFinished(4010,4,false);
  rejected.finish(HomeExitTrace.Result.HOME,4100);
  check(rejected.result==HomeExitTrace.Result.REJECTED&&rejected.desktopMillis<0,"a later launcher observation cannot rewrite a failed request");
  HomeExitTrace late=new HomeExitTrace(5000,9000);
  late.finish(HomeExitTrace.Result.HOME,4999);
  late.finish(HomeExitTrace.Result.TIMEOUT,6600);
  late.requestFinished(5010,2000,true);
  check(late.result==HomeExitTrace.Result.TIMEOUT&&late.desktopMillis<0,"stale and slow observations do not turn a timeout into success");
  System.out.println("PASS "+count+" transition checks");
 }
}
