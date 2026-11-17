package cn.sevendays.focus;

/** One local observation of an exit button press. Never controls the blocking policy. */
final class HomeExitTrace {
    enum Result {
        WAITING("等待桌面窗口"),
        HOME("已确认桌面窗口"),
        OTHER_WINDOW("已离开原应用，未确认桌面"),
        TIMEOUT("等待桌面窗口超时"),
        REJECTED("系统未接受返回请求"),
        INTERRUPTED("等待期间中断");

        final String label;
        Result(String label) { this.label = label; }
    }

    final long startedAt, wallAt;
    long overlayMillis = -1, requestDelayMillis = -1, requestMillis = -1, desktopMillis = -1;
    boolean overlayRequestOk;
    Result result = Result.WAITING;

    HomeExitTrace(long startedAt, long wallAt) {
        this.startedAt = startedAt;
        this.wallAt = wallAt;
    }

    void overlayRemoved(long at, boolean ok) {
        overlayMillis = Math.max(0, at - startedAt);
        overlayRequestOk = ok;
    }

    void requestFinished(long requestAt, long duration, boolean accepted) {
        requestDelayMillis = Math.max(0, requestAt - startedAt);
        requestMillis = duration;
        if (!accepted) finish(Result.REJECTED, requestAt + duration);
    }

    void finish(Result outcome, long at) {
        if (result != Result.WAITING || at < startedAt) return;
        result = outcome;
        if (outcome == Result.HOME) desktopMillis = at - startedAt;
    }
}
