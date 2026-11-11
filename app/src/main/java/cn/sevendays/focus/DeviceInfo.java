package cn.sevendays.focus;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.*;
import java.util.concurrent.TimeUnit;
final class DeviceInfo {
    private static String appTitle;
    final String manufacturer=DeviceIdentity.clean(Build.MANUFACTURER),brand=DeviceIdentity.clean(Build.BRAND),model=DeviceIdentity.clean(Build.MODEL);
    final String android=DeviceIdentity.clean(Build.VERSION.RELEASE),patch=DeviceIdentity.clean(Build.VERSION.SECURITY_PATCH),build=DeviceIdentity.clean(Build.DISPLAY);
    final int api=Build.VERSION.SDK_INT;
    final boolean vivo=DeviceIdentity.isVivo(manufacturer,brand);
    String market="",romName="",romVersion="";
    String title(){return DeviceIdentity.title(manufacturer,brand,model,market);}
    String rom(){return DeviceIdentity.rom(romName,romVersion);}
    void loadOptional(){
        market=first("ro.product.marketname","ro.vivo.market.name","ro.product.vivo.market.name");
        if(vivo){romName=prop("ro.vivo.os.name");romVersion=prop("ro.vivo.os.version");}
    }
    private String first(String... keys){for(String k:keys){String s=prop(k);if(!s.isEmpty())return s;}return "";}
    private String prop(String key){
        Process p=null;
        try{
            // Only an explicit, non-identifying allowlist is queried; no shell, private API or root.
            p=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();
            if(!p.waitFor(200,TimeUnit.MILLISECONDS)||p.exitValue()!=0)return "";
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){return DeviceIdentity.clean(r.readLine());}
        }catch(Exception ignored){return "";}finally{if(p!=null)p.destroy();}
    }
    static String appTitle(Context c){
        if(appTitle!=null)return appTitle;
        try{
            String version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
            appTitle="SevenDays"+(version==null||version.trim().isEmpty()?"":" "+version);
            return appTitle;
        }catch(PackageManager.NameNotFoundException|RuntimeException ignored){return "SevenDays";}
    }
    String report(Context c){return appTitle(c)+"\n设备："+title()+"\n厂商："+manufacturer+"\n品牌："+brand+"\n系统型号："+model+"\nAndroid："+android+"（API "+api+"）\n定制系统："+rom()+"\n安全补丁："+(patch.isEmpty()?"未公开":patch)+"\n构建版本："+build;}
}
