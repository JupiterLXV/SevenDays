package cn.sevendays.focus;
final class WarningPolicy {
    static int threshold(int budget,long remaining){if(remaining<=0)return 0;if(budget>1&&remaining<=60000)return 1;if(budget>5&&remaining<=300000)return 5;return 0;}
    static String key(String pkg,long day,int threshold){return pkg+":"+day+":"+threshold;}
}
