package cn.sevendays.focus;

/** A missed permission read does not mean the user turned off accessibility. */
final class ProtectionRunState {
    enum Authorization { ENABLED, DISABLED, UNKNOWN }
    enum Presentation {
        RUNNING("保护中", "计时与拦截正常", "查看设备与权限"),
        CONNECTING("待连接", "已授权，等待系统连接", "查看连接状态"),
        OFF("未开启", "无障碍服务未开启", "去开启无障碍"),
        UNKNOWN("待确认", "暂时无法确认无障碍状态", "检查无障碍设置"),
        ISSUE("保护异常", "保护检查异常", "查看运行诊断");

        final String label, message, action;
        Presentation(String label, String message, String action) {
            this.label = label;
            this.message = message;
            this.action = action;
        }
    }

    static Presentation presentation(Authorization authorization, boolean connected, boolean issue) {
        if (issue) return Presentation.ISSUE;
        if (authorization == Authorization.DISABLED) return Presentation.OFF;
        if (connected) return Presentation.RUNNING;
        return authorization == Authorization.ENABLED ? Presentation.CONNECTING : Presentation.UNKNOWN;
    }
    static final long DISABLED_GRACE_MS=120_000;
    private long disabledSince=-1;

    boolean keepRunning(boolean requested,Authorization authorization,long now){
        if(!requested)return false;
        if(authorization!=Authorization.DISABLED){disabledSince=-1;return true;}
        if(disabledSince<0)disabledSince=now;
        return now-disabledSince<DISABLED_GRACE_MS;
    }
}
