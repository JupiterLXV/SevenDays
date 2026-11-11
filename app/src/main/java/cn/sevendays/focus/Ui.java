package cn.sevendays.focus;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class Ui {
    static int BG,INK,MUTED,GREEN,SOFT,LINE,AMBER,SURFACE,PRIMARY_TEXT,HERO,HERO_TEXT,HERO_MUTED,PEACH,MOTION_TINT;
    static {palette(false);}
    static void theme(Context c){
        boolean dark=(c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;palette(dark);
        String id=c.getSharedPreferences("policy_v1",0).getString("orbit_theme","saturn");
        if(id.equals("saturn")){BG=dark?0xff181713:0xfff7f5ef;SURFACE=dark?0xff28241f:0xfffffefa;INK=dark?0xfff4ecdf:0xff342f29;MUTED=dark?0xffbcb09e:0xff756b5f;GREEN=dark?0xffd6b997:0xff7a5e42;SOFT=dark?0xff342d25:0xffece5d8;LINE=dark?0xff44392e:0xffe8e1d5;PRIMARY_TEXT=dark?0xff302417:0xfffffefa;}
        else if(id.equals("aurora")){BG=dark?0xff111e1c:0xfff1f7f4;SURFACE=dark?0xff1c2c29:0xfffcfffd;INK=dark?0xffe5f4ed:0xff293e38;MUTED=dark?0xffadc5b9:0xff5b7067;GREEN=dark?0xffa6d6c3:0xff316e61;SOFT=dark?0xff293d35:0xffe0ece5;LINE=dark?0xff3b5146:0xffd9e4dc;PRIMARY_TEXT=dark?0xff1e332b:0xffffffff;}
        else{BG=dark?0xff171420:0xfff5f4fa;SURFACE=dark?0xff252131:0xffffffff;INK=dark?0xffefeaf9:0xff302b40;MUTED=dark?0xffb9afc9:0xff726982;GREEN=dark?0xffc4b3ed:0xff6757a7;SOFT=dark?0xff332b44:0xffebe7f3;LINE=dark?0xff44395a:0xffe4deed;PRIMARY_TEXT=dark?0xff292038:0xffffffff;}
    }
    private static void palette(boolean dark){
        BG=dark?0xff171620:0xfff6f5fa;SURFACE=dark?0xff242230:0xffffffff;
        INK=dark?0xffeeecf7:0xff28263b;MUTED=dark?0xffb4aec8:0xff6e6a80;
        GREEN=dark?0xffc4baf4:0xff5a54aa;SOFT=dark?0xff302c43:0xffeceaf6;
        LINE=dark?0xff3d374f:0xffe6e3ef;AMBER=dark?0xfff1b296:0xff9c4d36;
        PRIMARY_TEXT=dark?0xff28213e:0xffffffff;HERO=dark?0xff383154:0xff5751a2;
        HERO_TEXT=0xfffaf8ff;HERO_MUTED=dark?0xffd4cae9:0xfff1ecff;
        PEACH=0xffefbaa9;MOTION_TINT=dark?0xff403857:0xffe0dcf7;
    }
    static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static int mixColorForMotion(){return MOTION_TINT;}
    static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static Drawable hero(Context c){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{HERO,INK==0xff28263b?0xff6a5fb0:0xff483b63});d.setCornerRadius(dp(c,24));return d;}
    static Drawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf((GREEN&0x00ffffff)|0x18000000),shape(color,dp(c,radius)),shape(Color.WHITE,dp(c,radius)));}
    static TextView text(Context c,String s,int sp,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(c,3),1);t.setFontFeatureSettings("kern");return t;}
    static TextView bold(Context c,String s,int sp){TextView t=text(c,s,sp,INK);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setBaselineAligned(false);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static void pad(View v,int x,int y){v.setPadding(dp(v.getContext(),x),dp(v.getContext(),y),dp(v.getContext(),x),dp(v.getContext(),y));}
    static void space(LinearLayout l,int n){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),n)));}
    static void gap(LinearLayout l,int n){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(dp(l.getContext(),n),1));}
    static void divider(LinearLayout l){View v=new View(l.getContext());v.setBackgroundColor(LINE);l.addView(v,new LinearLayout.LayoutParams(-1,dp(l.getContext(),1)));}
    static LinearLayout card(Context c){LinearLayout l=column(c);pad(l,16,16);l.setBackground(shape(SURFACE,dp(c,24)));return l;}
    static Button button(Context c,String s,boolean primary,Runnable action){
        Button b=new Button(c);b.setAllCaps(false);b.setText(s);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?PRIMARY_TEXT:GREEN}));b.setStateListAnimator(null);b.setElevation(0);b.setBackgroundTintList(null);android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(SOFT,dp(c,24)));states.addState(new int[]{},ripple(c,primary?GREEN:SOFT,24));b.setBackground(states);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));pad(b,16,10);b.setOnClickListener(v->{MotionUi.tap(b);action.run();});return b;
    }
    static TextView chip(Context c,String s,int color,int bg){TextView t=text(c,s,11,color);pad(t,9,5);t.setGravity(Gravity.CENTER);t.setBackground(shape(bg,dp(c,24)));return t;}
    static void clickable(View v,Runnable action){v.setClickable(true);v.setFocusable(true);v.setOnClickListener(x->action.run());}
    static String duration(long ms){long sec=(Math.max(0,ms)+999)/1000;return sec/60+" 分 "+sec%60+" 秒";}
    static String compact(long ms){long sec=(Math.max(0,ms)+999)/1000;return sec<3600?String.format(java.util.Locale.ROOT,"%02d:%02d",sec/60,sec%60):String.format(java.util.Locale.ROOT,"%d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60);}
    static String used(long ms){long m=ms/60000;if(ms>0&&m==0)return "不足 1 分钟";return m<60?m+" 分钟":m/60+" 小时 "+m%60+" 分";}
    static final class Icon extends View {
        final String name;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,String n,int color){super(c);name=n;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float size=Math.min(getWidth(),getHeight());canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();
            switch(name){
                case "back":canvas.drawLine(20,12,5,12,p);canvas.drawLine(5,12,11,6,p);canvas.drawLine(5,12,11,18,p);break;
                case "grip":p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++){canvas.drawCircle(8,6+i*6,1.4f,p);canvas.drawCircle(16,6+i*6,1.4f,p);}break;
                case "settings":for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2.5f,p);break;
                case "sliders":canvas.drawLine(6,3,6,21,p);canvas.drawLine(18,3,18,21,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(6,9,3,p);canvas.drawCircle(18,16,3,p);break;
                case "plus":canvas.drawLine(12,5,12,19,p);canvas.drawLine(5,12,19,12,p);break;
                case "close":canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);break;
                case "chevron":path.moveTo(9,6);path.lineTo(15,12);path.lineTo(9,18);canvas.drawPath(path,p);break;
                case "check":path.moveTo(5,12);path.lineTo(10,17);path.lineTo(19,7);canvas.drawPath(path,p);break;
                case "phone":canvas.drawRoundRect(6,2,18,22,3,3,p);canvas.drawLine(10,18,14,18,p);break;
                case "lock":canvas.drawRoundRect(5,10,19,21,3,3,p);canvas.drawArc(8,2,16,16,180,180,false,p);canvas.drawLine(12,14,12,17,p);break;
                case "shield":path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,14);path.quadTo(18,19,12,22);path.quadTo(6,19,4,14);path.lineTo(3,6);path.close();canvas.drawPath(path,p);canvas.drawLine(8,12,11,15,p);canvas.drawLine(11,15,16,9,p);break;
                case "search":canvas.drawCircle(10,10,6,p);canvas.drawLine(15,15,21,21,p);break;
                case "clock":canvas.drawCircle(12,12,9,p);canvas.drawLine(12,6,12,12,p);canvas.drawLine(12,12,16,14,p);break;
                case "grid":canvas.drawRoundRect(3,3,10,10,2,2,p);canvas.drawRoundRect(14,3,21,10,2,2,p);canvas.drawRoundRect(3,14,10,21,2,2,p);canvas.drawRoundRect(14,14,21,21,2,2,p);break;
                default:canvas.drawCircle(12,12,8,p);
            }canvas.restore();
        }
    }
}
