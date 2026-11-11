package cn.sevendays.focus;

/** A missed permission read does not mean the user turned off accessibility. */
final class ProtectionRunState {
    enum Authorization { ENABLED, DISABLED, UNKNOWN }
    static final long DISABLED_GRACE_MS=120_000;
    private long disabledSince=-1;

    boolean keepRunning(boolean requested,Authorization authorization,long now){
        if(!requested)return false;
        if(authorization!=Authorization.DISABLED){disabledSince=-1;return true;}
        if(disabledSince<0)disabledSince=now;
        return now-disabledSince<DISABLED_GRACE_MS;
    }
}
