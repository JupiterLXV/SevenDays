package cn.sevendays.probe;
public class Probe extends android.app.Activity {
 public void onCreate(android.os.Bundle b){super.onCreate(b);android.widget.TextView t=new android.widget.TextView(this);t.setText("BLOCKER TEST TARGET");t.setTextSize(24);t.setGravity(17);t.setBackgroundColor(0xffa8c9ad);setContentView(t);}
 protected void onResume(){super.onResume();if(getIntent().getBooleanExtra("pip",false)){getIntent().removeExtra("pip");new android.os.Handler(getMainLooper()).postDelayed(()->enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(4,3)).build()),400);}}
}