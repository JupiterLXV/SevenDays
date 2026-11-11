package cn.sevendays.focus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises a genuinely stalled remote reader, not a synchronous fake of the implementation. */
public class WindowReadQueueTests {
    static int count;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
    static void deliver(BlockingQueue<Runnable> ui)throws Exception{
        Runnable r=ui.poll(3,TimeUnit.SECONDS);if(r==null)throw new AssertionError("delivery timeout");r.run();
    }
    static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    public static void main(String[] args)throws Exception{
        BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger reads=new AtomicInteger(),concurrent=new AtomicInteger(),max=new AtomicInteger();
        List<Integer> delivered=new ArrayList<>();List<RuntimeException> errors=new ArrayList<>();
        WindowReadQueue<Integer> queue=new WindowReadQueue<>(worker,ui::add,()->{
            int n=reads.incrementAndGet(),active=concurrent.incrementAndGet();max.accumulateAndGet(active,Math::max);
            try{if(n==1){entered.countDown();await(release);}return n;}finally{concurrent.decrementAndGet();}
        },(value,error)->{if(error!=null)errors.add(error);else delivered.add(value);});
        try{
            queue.request();check(entered.await(1,TimeUnit.SECONDS),"remote reader started in background");
            long at=System.nanoTime();queue.invalidate();for(int i=0;i<10000;i++)queue.request();
            long callerMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-at);
            check(callerMs<250,"UI remains responsive while remote reader stalls ("+callerMs+"ms)");
            check(reads.get()==1,"a burst never queues 10000 remote queries");
            ExecutorService navigation=Executors.newSingleThreadExecutor();
            try{check(navigation.submit(()->true).get(1,TimeUnit.SECONDS),"independent HOME lane completes while window reader stalls");}finally{navigation.shutdownNow();}
            release.countDown();deliver(ui);
            check(delivered.isEmpty(),"pre-HOME snapshot cannot restore dismissed barrier");
            deliver(ui);check(delivered.equals(Arrays.asList(2)),"only fresh post-HOME snapshot is delivered");
            check(reads.get()==2&&max.get()==1,"events coalesce to one fresh read without overlap");
            check(errors.isEmpty(),"slow-read scenario has no spurious error");
        }finally{release.countDown();queue.close();}

        AtomicInteger attempt=new AtomicInteger();List<String> results=new ArrayList<>();
        WindowReadQueue<String> recovery=new WindowReadQueue<>(Executors.newSingleThreadExecutor(),ui::add,()->{
            if(attempt.incrementAndGet()==1)throw new IllegalStateException("target app unavailable");return "fresh";
        },(value,error)->results.add(error==null?value:"failed"));
        try{
            recovery.request();deliver(ui);check(results.equals(Arrays.asList("failed")),"query failure does not crash UI dispatcher");
            recovery.request();deliver(ui);check(results.equals(Arrays.asList("failed","fresh")),"next query recovers after remote failure");
        }finally{recovery.close();}

        List<String> shutdown=new ArrayList<>();
        WindowReadQueue<String> closed=new WindowReadQueue<>(Executors.newSingleThreadExecutor(),ui::add,()->"late",(v,e)->shutdown.add(v));
        closed.request();Runnable late=ui.poll(3,TimeUnit.SECONDS);check(late!=null,"completed result can wait for main dispatch");
        closed.close();late.run();check(shutdown.isEmpty(),"disconnected service cannot receive late result");
        closed.request();check(ui.isEmpty(),"closed service cannot schedule another query");
        System.out.println("PASS "+count+" asynchronous window checks");
    }
}
