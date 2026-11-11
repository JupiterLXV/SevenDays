package cn.sevendays.testhome;
public class TestHome extends android.app.Activity {public void onCreate(android.os.Bundle b){super.onCreate(b);android.widget.TextView t=new android.widget.TextView(this);t.setText("测试桌面\nHOME transition target");t.setGravity(17);t.setTextSize(24);t.setBackgroundColor(0xffdbe5f1);setContentView(t);}}
