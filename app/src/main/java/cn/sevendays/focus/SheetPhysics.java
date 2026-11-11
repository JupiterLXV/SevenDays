package cn.sevendays.focus;

/** Gesture decisions use display-independent distances; no policy or storage access. */
final class SheetPhysics {
    static float position(float travel, float height, float unit) {
        if (travel < 0) return -Math.min(18*unit, -travel*.18f);
        return Math.min(Math.max(0,height)+24*unit, travel);
    }
    static boolean dismiss(float offset, float speed, float height, float unit) {
        float threshold=Math.max(72*unit,Math.min(140*unit,height*.24f));
        return (offset>=threshold&&speed>-450*unit)||(offset>20*unit&&speed>900*unit);
    }
}
