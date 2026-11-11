package cn.sevendays.focus;
/** Common short quotas get more space; all ticks remain explicitly labelled. */
final class BandScale {
    static final int[] TICKS={0,15,30,60,120,240,720,1440};
    static float position(int minutes){int n=Math.max(0,Math.min(1440,minutes));for(int i=0;i<TICKS.length-1;i++)if(n<=TICKS[i+1])return (i+(n-TICKS[i])/(float)(TICKS[i+1]-TICKS[i]))/(TICKS.length-1);return 1;}
    static int minutes(float position){float p=Math.max(0,Math.min(1,position))*(TICKS.length-1);int i=Math.min(TICKS.length-2,(int)p);return Math.round(TICKS[i]+(p-i)*(TICKS[i+1]-TICKS[i]));}
}
