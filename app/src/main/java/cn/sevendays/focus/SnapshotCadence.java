package cn.sevendays.focus;

/** Preserve active usage promptly; only refresh the clock anchor while idle. */
final class SnapshotCadence {
    static final long ACTIVE_MS=1_000, IDLE_MS=60_000;
    private long last=-1, day=Long.MIN_VALUE;
    private boolean dirty;

    boolean due(long now,long currentDay,boolean charged,boolean writing,boolean failed){
        dirty|=charged||currentDay!=day;
        if(writing)return false;
        return last<0||now-last>=(dirty||failed?ACTIVE_MS:IDLE_MS);
    }
    void enqueued(long now,long currentDay){
        last=now;day=currentDay;dirty=false;
    }
}
