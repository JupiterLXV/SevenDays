package cn.sevendays.focus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/** Header-only sheet dragging leaves scrolling, text input and time bands to their controls. */
final class DragSheetDialog extends Dialog implements View.OnTouchListener {
    private View panel;
    private View touchSource;
    private VelocityTracker velocity;
    private ValueAnimator settling;
    private int pointer=-1;
    private float downX,downY,base,offset;
    private boolean dragged,rejected,closing,finished;
    private final float unit,slop,maxSpeed;

    DragSheetDialog(Context context) {
        super(context);
        unit=context.getResources().getDisplayMetrics().density;
        ViewConfiguration config=ViewConfiguration.get(context);
        slop=config.getScaledTouchSlop();maxSpeed=config.getScaledMaximumFlingVelocity();
    }
    void bind(View panel,View grip,View header) {
        this.panel=panel;grip.setOnTouchListener(this);header.setOnTouchListener(this);
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){release();stopAnimation();}
        });
    }
    @Override public boolean onTouch(View source,MotionEvent event) {
        if(panel==null||closing)return true;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            stopAnimation();panel.animate().cancel();panel.setAlpha(1);
            release();pointer=event.getPointerId(0);downX=event.getRawX();downY=event.getRawY();offset=panel.getScaleY()>1?-(panel.getScaleY()-1)*panel.getHeight():panel.getTranslationY();base=offset;dragged=rejected=false;
            touchSource=source;source.setPressed(true);velocity=VelocityTracker.obtain();velocity.addMovement(event);return true;
        }
        if(pointer<0)return true;
        if(action==MotionEvent.ACTION_POINTER_DOWN||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer)){
            release();settle(false,false);return true;
        }
        if(velocity!=null)velocity.addMovement(event);
        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
        if(action==MotionEvent.ACTION_MOVE){
            if(!dragged&&!rejected&&Math.hypot(dx,dy)>slop){if(Math.abs(dy)>Math.abs(dx)*1.15f)dragged=true;else rejected=true;}
            if(dragged||rejected)source.setPressed(false);if(dragged)apply(SheetPhysics.position(base+dy,panel.getHeight(),unit));
            return true;
        }
        if(action==MotionEvent.ACTION_UP){
            float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000,maxSpeed);speed=velocity.getYVelocity(pointer);}
            boolean moved=dragged,shouldClose=moved&&SheetPhysics.dismiss(offset,speed,panel.getHeight(),unit);
            release();settle(moved&&shouldClose,false);return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){release();settle(false,false);return true;}
        return true;
    }
    private void apply(float value){offset=value;if(panel==null)return;panel.setPivotY(panel.getHeight());panel.setTranslationY(Math.max(0,value));panel.setScaleY(value<0?1+Math.min(.025f,-value/Math.max(1,panel.getHeight())):1);}
    private void release(){pointer=-1;if(touchSource!=null){touchSource.setPressed(false);touchSource=null;}if(velocity!=null){velocity.recycle();velocity=null;}}
    private void stopAnimation(){if(settling!=null){ValueAnimator old=settling;settling=null;old.cancel();}}
    void close(boolean cancelled){if(closing||!isShowing())return;release();settle(true,cancelled);}
    private void settle(boolean dismiss,boolean cancelled){
        stopAnimation();closing=dismiss;
        if(!dismiss&&Math.abs(offset)<.1f){apply(0);return;}
        float target=dismiss?Math.max(panel.getHeight()+24*unit,offset):0;
        if(!MotionUi.enabled(getContext())){apply(target);if(dismiss)finish(cancelled);return;}
        settling=ValueAnimator.ofFloat(offset,target);settling.setDuration(dismiss?180:240);settling.setInterpolator(MotionUi.EASE);
        settling.addUpdateListener(a->apply((float)a.getAnimatedValue()));
        settling.addListener(new AnimatorListenerAdapter(){boolean aborted;@Override public void onAnimationCancel(Animator a){aborted=true;}@Override public void onAnimationEnd(Animator a){if(aborted)return;settling=null;if(dismiss)finish(cancelled);}});settling.start();
    }
    private void finish(boolean cancelled){finished=true;if(cancelled)super.cancel();else super.dismiss();}
    @Override public void cancel(){if(finished||panel==null)super.cancel();else close(true);}
    @Override public void onBackPressed(){cancel();}
    @Override public void dismiss(){release();stopAnimation();finished=true;super.dismiss();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){return closing||super.dispatchTouchEvent(event);}

    /** A weighted body shrinks before the fixed header/footer when the keyboard or window gets smaller. */
    static final class Panel extends LinearLayout {
        Panel(Context context){super(context);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int max=Math.round(getResources().getDisplayMetrics().heightPixels*.9f);
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)max=Math.min(max,MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(Math.max(1,max),MeasureSpec.AT_MOST));
        }
    }
}
