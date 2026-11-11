package cn.sevendays.focus;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** One remote window query at a time. Public methods and delivery run on the UI dispatcher. */
final class WindowReadQueue<T> implements AutoCloseable {
    interface Sink<T> { void accept(T value, RuntimeException error); }
    private final ExecutorService worker;
    private final Executor delivery;
    private final Supplier<T> read;
    private final Sink<T> sink;
    private boolean running, requested, closed;
    private long generation;

    WindowReadQueue(ExecutorService worker, Executor delivery, Supplier<T> read, Sink<T> sink) {
        this.worker=worker;this.delivery=delivery;this.read=read;this.sink=sink;
    }
    void request() { if(!closed){requested=true;start();} }
    void invalidate() { generation++; }
    private void start() {
        if(closed||running||!requested)return;
        running=true;requested=false;long token=generation;
        worker.execute(()->{
            T value=null;RuntimeException error=null;
            try{value=read.get();}catch(RuntimeException e){error=e;}
            final T result=value;final RuntimeException failure=error;
            delivery.execute(()->{
                running=false;
                if(closed)return;
                // A result captured before HOME, screen-off or rotation must never restore a mask.
                if(token==generation)sink.accept(result,failure);
                start();
            });
        });
    }
    @Override public void close() { closed=true;requested=false;generation++;worker.shutdownNow(); }
}
