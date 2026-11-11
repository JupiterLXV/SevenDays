package cn.sevendays.focus;
import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.function.IntConsumer;
final class FlowUi {
    static final class Expansion extends FrameLayout {
        float value,velocity,target;long last,started;Runnable settled;
        final Runnable frame=new Runnable(){public void run(){if(!isAttachedToWindow())return;long now=SystemClock.uptimeMillis();float dt=Math.min(.032f,Math.max(.001f,(now-last)/1000f));last=now;velocity+=((target-value)*420-velocity*38)*dt;value+=velocity*dt;if(now-started>900||(Math.abs(target-value)<.0015f&&Math.abs(velocity)<.015f)){value=target;velocity=0;requestLayout();if(settled!=null)settled.run();return;}requestLayout();postOnAnimation(this);}};
        Expansion(Context c){super(c);setClipChildren(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        void open(boolean open,boolean animate){target=open?1:0;setImportantForAccessibility(open?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);if(!open)clearFocus();removeCallbacks(frame);if(!animate||!MotionUi.enabled(getContext())){value=target;velocity=0;requestLayout();if(settled!=null)settled.run();return;}last=started=SystemClock.uptimeMillis();postOnAnimation(frame);}
        @Override protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws);View child=getChildCount()==0?null:getChildAt(0);if(child!=null)child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));setMeasuredDimension(width,child==null?0:Math.round(child.getMeasuredHeight()*MotionUi.clamp(value)));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){if(getChildCount()>0){View v=getChildAt(0);v.layout(0,0,getWidth(),v.getMeasuredHeight());v.setAlpha(MotionUi.clamp(value*1.7f));}}
        @Override protected void onDetachedFromWindow(){removeCallbacks(frame);value=target;velocity=0;super.onDetachedFromWindow();}
    }
    static final class RemainingBand extends View {
        final Paint p=new Paint(3);float fraction;boolean near;
        RemainingBand(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void update(float value,boolean near){if(this.fraction==value&&this.near==near)return;fraction=MotionUi.clamp(value);this.near=near;invalidate();}
        @Override protected void onDraw(Canvas c){float h=getHeight();p.setColor(Ui.SOFT);c.drawRoundRect(0,0,getWidth(),h,h,h,p);if(fraction>0){p.setColor(near?Ui.AMBER:Ui.GREEN);c.drawRoundRect(0,0,Math.max(h,getWidth()*fraction),h,h,h,p);}}
    }
    static final class TimeBand extends View {
        final Paint p=new Paint(3);final OverScroller scroller;VelocityTracker tracker;IntConsumer listener;
        float position,downX,downY,lastX;int value;boolean dragging,snapping;long lastHaptic;
        TimeBand(Context c,int minutes){super(c);value=Math.max(0,Math.min(1440,minutes));scroller=new OverScroller(c);scroller.setFriction(.065f);setFocusable(true);setClickable(true);describe();}
        float padding(){return Ui.dp(getContext(),18);}float span(){return Math.max(1,getWidth()-padding()*2);}
        void setValue(int n){scroller.forceFinished(true);snapping=false;value=Math.max(0,Math.min(1440,n));position=BandScale.position(value)*span();describe();invalidate();}
        void describe(){setContentDescription("每日额度 "+value+" 分钟，滑动时间带调整；也可直接输入数字");}
        void publish(){int n=BandScale.minutes(position/span());if(n!=value){int old=value;value=n;describe();if(listener!=null)listener.accept(n);long now=SystemClock.uptimeMillis();if(old/5!=n/5&&now-lastHaptic>50){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);lastHaptic=now;}}invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);setValue(value);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),290),w),resolveSize(Ui.dp(getContext(),116),h));}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,pad=padding(),width=span(),x=pad+position,y=39*d;float end=Math.max(pad,Math.min(getWidth()-pad,x));p.setColor(Ui.SOFT);c.drawRoundRect(pad,y-10*d,getWidth()-pad,y+10*d,10*d,10*d,p);if(end>pad){p.setColor(Ui.GREEN);c.drawRoundRect(pad,y-10*d,end,y+10*d,10*d,10*d,p);}p.setStrokeCap(Paint.Cap.ROUND);float fs=getResources().getConfiguration().fontScale;for(int i=0;i<BandScale.TICKS.length;i++){float tx=pad+width*i/(BandScale.TICKS.length-1);p.setColor(Ui.MUTED);p.setStrokeWidth(d);c.drawLine(tx,60*d,tx,65*d,p);if(fs>1.3f&&i!=0&&i!=2&&i!=4&&i!=7)continue;p.setTextSize(11*d*fs);p.setTextAlign(i==0?Paint.Align.LEFT:i==7?Paint.Align.RIGHT:Paint.Align.CENTER);c.drawText(String.valueOf(BandScale.TICKS[i]),tx,88*d,p);}p.setColor(Ui.PEACH);c.save();c.rotate(-24,x,y);c.drawRoundRect(x-10*d,y-19*d,x+10*d,y+19*d,10*d,10*d,p);c.restore();p.setColor(Ui.HERO);c.drawCircle(x,y,3*d,p);}
        private void release(){dragging=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(tracker!=null){tracker.recycle();tracker=null;}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
            case android.view.MotionEvent.ACTION_DOWN:scroller.forceFinished(true);snapping=false;downX=lastX=e.getX();downY=e.getY();dragging=true;if(tracker!=null)tracker.recycle();tracker=VelocityTracker.obtain();tracker.addMovement(e);getParent().requestDisallowInterceptTouchEvent(true);return true;
            case android.view.MotionEvent.ACTION_MOVE:if(!dragging)return false;tracker.addMovement(e);if(Math.abs(e.getY()-downY)>Ui.dp(getContext(),12)&&Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)){release();snap();return false;}float dx=e.getX()-lastX;if(position<0||position>span())dx*=.25f;position=Math.max(-Ui.dp(getContext(),12),Math.min(span()+Ui.dp(getContext(),12),position+dx));lastX=e.getX();publish();return true;
            case android.view.MotionEvent.ACTION_UP:if(!dragging)return false;tracker.addMovement(e);tracker.computeCurrentVelocity(1000,1200);float speed=tracker.getXVelocity();if(Math.abs(e.getX()-downX)<ViewConfiguration.get(getContext()).getScaledTouchSlop()){position=e.getX()-padding();publish();speed=0;performClick();}release();if(!MotionUi.enabled(getContext())){position=BandScale.position(value)*span();invalidate();return true;}if(!scroller.springBack(Math.round(position),0,0,Math.round(span()),0,0))scroller.fling(Math.round(position),0,(int)speed,0,0,Math.round(span()),0,0,Ui.dp(getContext(),12),0);postInvalidateOnAnimation();return true;
            case android.view.MotionEvent.ACTION_CANCEL:release();snap();return true;
        }return super.onTouchEvent(e);}
        void snap(){float target=BandScale.position(value)*span();if(Math.abs(position-target)>.5f&&MotionUi.enabled(getContext())){snapping=true;scroller.startScroll(Math.round(position),0,Math.round(target-position),0,140);postInvalidateOnAnimation();}else{boolean change=position!=target;position=target;snapping=false;if(change)invalidate();}}
        @Override public void computeScroll(){if(scroller.computeScrollOffset()){position=scroller.getCurrX();publish();postInvalidateOnAnimation();}else if(!dragging){if(!snapping)snap();else{snapping=false;position=BandScale.position(value)*span();invalidate();}}}
        @Override public boolean performClick(){super.performClick();return true;}
        void adjust(int delta){setValue(value+delta);if(listener!=null)listener.accept(value);MotionUi.tap(this);}
        @Override public boolean onKeyDown(int code,android.view.KeyEvent e){if(code==android.view.KeyEvent.KEYCODE_DPAD_LEFT||code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){adjust(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return true;}return super.onKeyDown(code,e);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName(SeekBar.class.getName());n.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,1440,value));n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){adjust(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?5:-5);return true;}if(action==android.R.id.accessibilityActionSetProgress&&args!=null){setValue(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));if(listener!=null)listener.accept(value);return true;}return super.performAccessibilityAction(action,args);}
        @Override protected void onDetachedFromWindow(){scroller.forceFinished(true);if(tracker!=null){tracker.recycle();tracker=null;}super.onDetachedFromWindow();}
    }
    static final class SuccessMark extends View {
        final Paint p=new Paint(3);float progress;ValueAnimator animator;
        SuccessMark(Context c){super(c);setContentDescription("规则已保存");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void play(){if(!MotionUi.enabled(getContext())){progress=1;invalidate();return;}animator=ValueAnimator.ofFloat(0,1);animator.setDuration(420);animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density;p.setColor(Ui.GREEN);c.drawRoundRect(0,0,getWidth(),getHeight(),18*d,18*d,p);p.setColor(Ui.PRIMARY_TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setStrokeCap(Paint.Cap.ROUND);Path full=new Path();full.moveTo(13*d,23*d);full.lineTo(18*d,28*d);full.lineTo(28*d,16*d);Path part=new Path();PathMeasure m=new PathMeasure(full,false);m.getSegment(0,m.getLength()*Math.min(1,progress*2),part,true);c.drawPath(part,p);p.setStyle(Paint.Style.FILL);p.setTextSize(13*d*getResources().getConfiguration().fontScale);p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText("已保存",36*d,27*d,p);if(progress>.5f&&progress<1){float t=(progress-.5f)*2;p.setColor(Ui.PEACH);p.setAlpha((int)(255*(1-t)));for(int i=0;i<6;i++){double a=i*Math.PI/3;float r=(11+7*t)*d;c.drawCircle(21*d+(float)Math.cos(a)*r,22*d+(float)Math.sin(a)*r,1.4f*d,p);}p.setAlpha(255);}}
        @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }
}
