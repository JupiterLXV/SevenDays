package cn.sevendays.focus;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.util.function.IntConsumer;

/** Finite, UI-only motion. Saving and quota enforcement never depend on animation callbacks. */
final class MotionUi {
    static final android.view.animation.Interpolator EASE=new PathInterpolator(.2f,0f,0f,1f);
    static boolean enabled(Context c){return ValueAnimator.areAnimatorsEnabled();}
    static float clamp(float n){return Math.max(0,Math.min(1,n));}
    static int mix(int a,int b,float t){return Color.argb(255,(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    static void tap(View v){v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}

    static final class CompleteButton extends View {
        final Paint paint=new Paint(3);final Path check=new Path();ValueAnimator animation;
        float phase;boolean busy,done;Runnable finished;String title;
        CompleteButton(Context c,String label){super(c);title=label;setClickable(true);setFocusable(true);setMinimumHeight(Ui.dp(c,64));describe();}
        void describe(){setContentDescription(done?"已完成，规则已保存":busy?"正在显示完成反馈":title);}
        void celebrate(Runnable end){if(busy||done)return;busy=true;finished=end;describe();tap(this);announceForAccessibility("规则已保存，已锁定七天");
            if(!enabled(getContext())){phase=1;finish();return;}
            animation=ValueAnimator.ofFloat(0,1);animation.setDuration(920);animation.setInterpolator(new android.view.animation.LinearInterpolator());animation.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animation.addListener(new AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(Animator a){cancelled=true;}@Override public void onAnimationEnd(Animator a){if(!cancelled)finish();}});animation.start();
        }
        void finish(){done=true;busy=false;describe();invalidate();Runnable r=finished;finished=null;if(r!=null)r.run();}
        @Override public boolean performClick(){if(busy||done||!isEnabled())return false;tap(this);return super.performClick();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!isEnabled()||busy||done)return false;if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){setPressed(true);invalidate();return true;}if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){boolean click=isPressed()&&e.getX()>=0&&e.getX()<=getWidth()&&e.getY()>=0&&e.getY()<=getHeight();setPressed(false);invalidate();if(click)performClick();return true;}if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){setPressed(false);invalidate();}return true;}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),260),w),resolveSize(Ui.dp(getContext(),96),h));}
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,h=Math.min(getHeight()-Ui.dp(getContext(),16),Ui.dp(getContext(),52));float morph=EASE.getInterpolation(clamp(phase/.34f));float width=(getWidth()-Ui.dp(getContext(),24))*(1-morph)+h*morph;
            c.save();if(isPressed())c.scale(.975f,.975f,cx,cy);paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.GREEN);paint.setAlpha(isEnabled()?255:95);c.drawRoundRect(cx-width/2,cy-h/2,cx+width/2,cy+h/2,h/2,h/2,paint);
            if(phase<.30f){paint.setColor(Ui.PRIMARY_TEXT);paint.setAlpha((int)(255*(1-clamp(phase/.24f))));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextSize(Ui.dp(getContext(),14)*getResources().getConfiguration().fontScale);while(paint.measureText(title)>width-Ui.dp(getContext(),22)&&paint.getTextSize()>Ui.dp(getContext(),10))paint.setTextSize(paint.getTextSize()-1);Paint.FontMetrics f=paint.getFontMetrics();c.drawText(title,cx,cy-(f.ascent+f.descent)/2,paint);}
            float tick=clamp((phase-.34f)/.27f);if(tick>0){paint.setAlpha(255);paint.setColor(Ui.PRIMARY_TEXT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);check.reset();check.moveTo(cx-h*.20f,cy);check.lineTo(cx-h*.045f,cy+h*.15f);check.lineTo(cx+h*.23f,cy-h*.16f);PathMeasure measure=new PathMeasure(check,false);Path part=new Path();measure.getSegment(0,measure.getLength()*tick,part,true);c.drawPath(part,paint);}
            float burst=clamp((phase-.60f)/.40f);if(burst>0){int[] colors={Ui.GREEN,Ui.PEACH,Ui.MOTION_TINT,Ui.HERO_MUTED};paint.setStyle(Paint.Style.FILL);for(int i=0;i<16;i++){double angle=Math.PI*2*i/16-.15;float radius=h*.52f+Ui.dp(getContext(),16)*EASE.getInterpolation(burst);paint.setColor(colors[i%4]);paint.setAlpha((int)(255*(1-burst)));float x=cx+(float)Math.cos(angle)*radius,y=cy+(float)Math.sin(angle)*radius;c.drawCircle(x,y,Ui.dp(getContext(),i%3==0?3.3f:2.2f)*(1-burst*.4f),paint);}}
            paint.setAlpha(255);c.restore();
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());}
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();finished=null;super.onDetachedFromWindow();}
    }

    /** Ring, integer and surface color are driven by the same frame value. */
    static final class ChecklistHeader extends View {
        final Paint p=new Paint(3);ValueAnimator animation;float progress;int completed,total=3;
        ChecklistHeader(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        void progress(int count,int max,boolean animate){completed=count;total=max;setContentDescription("确认清单，已完成 "+count+" 项，共 "+max+" 项");if(animation!=null)animation.cancel();float target=max==0?0:count/(float)max;
            if(!animate||!enabled(getContext())){progress=target;invalidate();return;}animation=ValueAnimator.ofFloat(progress,target);animation.setDuration(360);animation.setInterpolator(EASE);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.start();
        }
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),280),w),resolveSize(Ui.dp(getContext(),100),h));}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,fs=getResources().getConfiguration().fontScale;
            p.setStyle(Paint.Style.FILL);p.setColor(mix(Ui.SOFT,Ui.mixColorForMotion(),progress));c.drawRoundRect(0,0,getWidth(),getHeight(),24*d,24*d,p);float x=42*d,y=getHeight()/2f,r=24*d;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*d);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(mix(Ui.LINE,Ui.SURFACE,.3f));c.drawCircle(x,y,r,p);p.setColor(Ui.GREEN);c.drawArc(x-r,y-r,x+r,y+r,-90,360*progress,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Ui.GREEN);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(16*d*Math.min(fs,1.35f));Paint.FontMetrics fm=p.getFontMetrics();c.drawText(Math.round(progress*total)+"/"+total,x,y-(fm.ascent+fm.descent)/2,p);
            p.setTextAlign(Paint.Align.LEFT);p.setColor(Ui.INK);p.setTextSize(15*d*Math.min(fs,1.3f));c.drawText(completed==total?"已确认":"设置确认",82*d,y-5*d,p);p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(11*d*Math.min(fs,1.25f));p.setColor(Ui.MUTED);c.drawText("额度 · 恢复规则 · 七天锁定",82*d,y+17*d,p);
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

    static final class StackCards extends android.view.ViewGroup {
        ValueAnimator animation;float progress;boolean expanded;int expandedHeight,collapsedHeight;Runnable changed;final Paint backing=new Paint(3);
        StackCards(Context c,boolean open){super(c);expanded=open;progress=open?1:0;setClipChildren(true);setChildrenDrawingOrderEnabled(true);}
        void toggle(){expanded=!expanded;tap(this);if(animation!=null)animation.cancel();if(changed!=null)changed.run();
            if(!enabled(getContext())){progress=expanded?1:0;requestLayout();return;}
            animation=ValueAnimator.ofFloat(progress,expanded?1:0);animation.setDuration(Math.min(560,340+getChildCount()*30));animation.setInterpolator(EASE);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();requestLayout();});animation.start();announceForAccessibility(expanded?"应用卡片已展开":"应用卡片已叠起");
        }
        private float at(int index){float delay=Math.min(.28f,index*.055f);return clamp((progress-delay)/(1-delay));}
        @Override protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws);expandedHeight=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));expandedHeight+=child.getMeasuredHeight()+(i==0?0:Ui.dp(getContext(),12));}collapsedHeight=getChildCount()==0?0:getChildAt(0).getMeasuredHeight()+Ui.dp(getContext(),12)*Math.min(2,getChildCount()-1);int height=(int)(collapsedHeight+(expandedHeight-collapsedHeight)*progress);setMeasuredDimension(width,height);}
        @Override protected void onLayout(boolean b,int l,int t,int r,int bot){int y=0;for(int i=0;i<getChildCount();i++){View v=getChildAt(i);float local=at(i);int cy=Ui.dp(getContext(),12)*Math.min(i,2);int top=(int)(cy+(y-cy)*local);v.layout(0,top,getWidth(),top+v.getMeasuredHeight());float scale=1-.045f*Math.min(i,2)*(1-local);v.setPivotX(getWidth()/2f);v.setPivotY(0);v.setScaleX(scale);v.setAlpha(i>0?local:1);v.setVisibility(i>0&&progress==0?INVISIBLE:VISIBLE);v.setImportantForAccessibility(i>0&&progress<1?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);v.setElevation(0);y+=v.getMeasuredHeight()+Ui.dp(getContext(),12);}}
        @Override protected void dispatchDraw(Canvas canvas){if(getChildCount()>1&&progress<1){float d=getResources().getDisplayMetrics().density;float base=getChildAt(0).getMeasuredHeight();for(int i=Math.min(2,getChildCount()-1);i>=1;i--){float inset=i*7*d*(1-progress),offset=i*12*d*(1-progress);backing.setColor(mix(Ui.SURFACE,Ui.GREEN,i==1?.12f:.23f));backing.setAlpha((int)(255*(1-progress)));canvas.drawRoundRect(inset,offset,getWidth()-inset,base+offset,24*d,24*d,backing);}}super.dispatchDraw(canvas);}
        @Override protected int getChildDrawingOrder(int count,int order){return count-1-order;}
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

    /** Horizontal virtual ruler, with real velocity, bounded overfling and exact-minute settling. */
    static final class MinuteWheel extends View {
        final Paint p=new Paint(3);final OverScroller scroller;VelocityTracker velocity;IntConsumer listener;
        float position,lastX,downX,downY;final float unit;boolean dragging,snapping;int value=30;long lastHaptic;
        MinuteWheel(Context c,int initial){super(c);unit=Ui.dp(c,7);position=Math.max(0,Math.min(1440,initial))*unit;value=Math.round(position/unit);scroller=new OverScroller(c);scroller.setFriction(.022f);setFocusable(true);setClickable(true);describe();}
        void describe(){setContentDescription("每日额度 "+value+" 分钟，横向滑动刻度或使用左右方向键调整");}
        void setValue(int n){scroller.forceFinished(true);snapping=false;value=Math.max(0,Math.min(1440,n));position=value*unit;describe();invalidate();}
        void publish(){int n=Math.max(0,Math.min(1440,Math.round(position/unit)));if(n!=value){int old=value;value=n;describe();if(listener!=null)listener.accept(n);long now=SystemClock.uptimeMillis();if(old/5!=n/5&&now-lastHaptic>50){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);lastHaptic=now;}}invalidate();}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(Ui.dp(getContext(),280),w),resolveSize(Ui.dp(getContext(),78),h));}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f; p.setStyle(Paint.Style.FILL);p.setColor(Ui.SOFT);c.drawRoundRect(0,0,getWidth(),getHeight(),20*d,20*d,p);c.save();c.clipRect(8*d,0,getWidth()-8*d,getHeight());int center=(int)(position/unit),range=(int)(getWidth()/unit/2)+3;for(int n=Math.max(0,center-range);n<=Math.min(1440,center+range);n++){float x=cx+n*unit-position,fade=1-Math.min(1,Math.abs(x-cx)/(getWidth()*.55f));p.setColor(Ui.MUTED);p.setAlpha((int)(60+170*fade));p.setStrokeWidth((n%5==0?2:1)*d);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,21*d,x,(n%5==0?40:32)*d,p);if(n%10==0){p.setTextSize(10*d);p.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf(n),x,57*d,p);}}p.setAlpha(255);p.setColor(Ui.GREEN);p.setStrokeWidth(3*d);c.drawLine(cx,13*d,cx,43*d,p);c.drawCircle(cx,9*d,2*d,p);c.restore();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
            case android.view.MotionEvent.ACTION_DOWN:scroller.forceFinished(true);snapping=false;downX=lastX=e.getX();downY=e.getY();dragging=true;if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(e);getParent().requestDisallowInterceptTouchEvent(true);return true;
            case android.view.MotionEvent.ACTION_MOVE:if(!dragging)return false;velocity.addMovement(e);float dx=lastX-e.getX();if(Math.abs(e.getY()-downY)>Ui.dp(getContext(),12)&&Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);release(false);return false;}if(position<0||position>1440*unit)dx*=.28f;position=Math.max(-Ui.dp(getContext(),56),Math.min(1440*unit+Ui.dp(getContext(),56),position+dx));lastX=e.getX();publish();return true;
            case android.view.MotionEvent.ACTION_UP:if(!dragging)return false;velocity.addMovement(e);velocity.computeCurrentVelocity(1000,ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());float speed=-velocity.getXVelocity();if(Math.abs(e.getX()-downX)<ViewConfiguration.get(getContext()).getScaledTouchSlop()){position+=(e.getX()-getWidth()/2f);publish();performClick();speed=0;}release(true);if(!enabled(getContext())){position=value*unit;publish();return true;}if(scroller.springBack(Math.round(position),0,0,Math.round(1440*unit),0,0)){postInvalidateOnAnimation();return true;}scroller.fling(Math.round(position),0,(int)speed,0,0,Math.round(1440*unit),0,0,Ui.dp(getContext(),50),0);postInvalidateOnAnimation();return true;
            case android.view.MotionEvent.ACTION_CANCEL:release(false);settle();return true;
        }return super.onTouchEvent(e);}
        private void release(boolean up){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);if(velocity!=null){velocity.recycle();velocity=null;}}
        private void settle(){int target=Math.max(0,Math.min(1440,Math.round(position/unit)))*Math.round(unit);if(!enabled(getContext())){if(position!=target){position=target;publish();}return;}if(Math.abs(position-target)>.5f){snapping=true;scroller.startScroll(Math.round(position),0,target-Math.round(position),0,180);postInvalidateOnAnimation();}else{boolean changed=position!=target;position=target;snapping=false;if(changed)publish();}}
        @Override public void computeScroll(){if(scroller.computeScrollOffset()){position=scroller.getCurrX();publish();postInvalidateOnAnimation();}else if(!dragging){if(!snapping)settle();else{snapping=false;position=value*unit;invalidate();}}}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onKeyDown(int code,android.view.KeyEvent e){if(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT||code==android.view.KeyEvent.KEYCODE_DPAD_LEFT){adjust(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return true;}return super.onKeyDown(code,e);}
        private void adjust(int delta){setValue(value+delta);if(listener!=null)listener.accept(value);tap(this);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,1440,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);}
        @Override public boolean performAccessibilityAction(int action,android.os.Bundle b){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){adjust(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?5:-5);return true;}if(action==android.R.id.accessibilityActionSetProgress&&b!=null){setValue(Math.round(b.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));if(listener!=null)listener.accept(value);return true;}return super.performAccessibilityAction(action,b);}
        @Override protected void onDetachedFromWindow(){scroller.forceFinished(true);if(velocity!=null){velocity.recycle();velocity=null;}super.onDetachedFromWindow();}
    }

    /** Snaps only after touch and fling stop, then notifies the timeline. */
    static final class SnapDays extends HorizontalScrollView {
        final LinearLayout cards;final int count;int selected,step,initial;boolean finger;IntConsumer settled;
        final Runnable idle=()->settle();
        SnapDays(Context c,int n,int index){super(c);count=n;initial=index;selected=index;setHorizontalScrollBarEnabled(false);setClipToPadding(false);cards=Ui.row(c);addView(cards);}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws);if(w>0){int width=Math.max(Ui.dp(getContext(),100),w-Ui.dp(getContext(),24));int side=(w-width)/2;setPadding(side,0,side,0);step=width+Ui.dp(getContext(),10);for(int i=0;i<cards.getChildCount();i++){View child=cards.getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();int margin=i==count-1?0:Ui.dp(getContext(),10);if(lp.width!=width||lp.rightMargin!=margin){lp.width=width;lp.height=-1;lp.rightMargin=margin;child.setLayoutParams(lp);}}}super.onMeasure(ws,hs);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);post(()->{scrollTo(target(Math.max(0,initial>=0?initial:selected)),0);schedule();});}
        int target(int index){View child=cards.getChildAt(Math.max(0,Math.min(count-1,index)));return Math.max(0,Math.round(child.getLeft()+child.getWidth()/2f+getPaddingLeft()-getWidth()/2f));}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){finger=true;removeCallbacks(idle);}boolean result=super.onTouchEvent(e);if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP||e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){finger=false;schedule();}return result;}
        @Override public void fling(int velocity){super.fling(Math.max(-4500,Math.min(4500,velocity)));schedule();}
        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);schedule();}
        void schedule(){removeCallbacks(idle);if(!finger)postDelayed(idle,100);}
        private void settle(){if(finger||step<=0||!isAttachedToWindow())return;int index=0;float best=Float.MAX_VALUE;for(int i=0;i<count;i++){float distance=Math.abs(target(i)-getScrollX());if(distance<best){best=distance;index=i;}}int target=target(index);if(Math.abs(getScrollX()-target)>1){if(enabled(getContext()))smoothScrollTo(target,0);else scrollTo(target,0);schedule();return;}if(selected!=index||initial>=0){selected=index;initial=-1;for(int i=0;i<cards.getChildCount();i++){View v=cards.getChildAt(i);v.setSelected(i==index);v.setAlpha(i==index?1:.65f);}if(settled!=null)settled.accept(index);}}
        void choose(int index){if(step<=0)return;if(enabled(getContext()))smoothScrollTo(target(index),0);else scrollTo(target(index),0);schedule();}
        @Override protected void onDetachedFromWindow(){removeCallbacks(idle);super.onDetachedFromWindow();}
    }

    static final class Timeline extends View {
        final Paint p=new Paint(3);ValueAnimator animation;float progress;
        Timeline(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void drawSteps(){if(animation!=null)animation.cancel();if(!enabled(getContext())){progress=1;invalidate();return;}animation=ValueAnimator.ofFloat(0,1);animation.setDuration(640);animation.setInterpolator(EASE);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.start();}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=Ui.dp(getContext(),20),end=getHeight()-Ui.dp(getContext(),20),mid=(y+end)/2;p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Ui.dp(getContext(),3));p.setColor(Ui.LINE);c.drawLine(x,y,x,end,p);p.setColor(Ui.GREEN);float first=clamp(progress*2),second=clamp(progress*2-1);c.drawLine(x,y,x,y+(mid-y)*first,p);if(second>0)c.drawLine(x,mid,x,mid+(end-mid)*second,p);for(int i=0;i<3;i++){float at=y+i*(end-y)/2;p.setColor(progress*2>=i?Ui.GREEN:Ui.LINE);c.drawCircle(x,at,Ui.dp(getContext(),5),p);p.setColor(Ui.SURFACE);c.drawCircle(x,at,Ui.dp(getContext(),2),p);}}
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }
}
