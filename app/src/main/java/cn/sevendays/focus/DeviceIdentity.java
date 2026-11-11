package cn.sevendays.focus;
import java.util.Locale;
/** System values only: never infer a retail model or OriginOS version from an internal model code. */
public final class DeviceIdentity {
    public static String clean(String s){if(s==null)return "";s=s.replaceAll("[\\p{Cntrl}]", " ").trim();return s.equalsIgnoreCase("unknown")||s.equalsIgnoreCase("null")?"":s.substring(0,Math.min(160,s.length()));}
    public static boolean isVivo(String manufacturer,String brand){String v=(clean(manufacturer)+" "+clean(brand)).toLowerCase(Locale.ROOT);return v.contains("vivo")||v.contains("iqoo");}
    public static String title(String manufacturer,String brand,String model,String market){
        String m=clean(market);if(!m.isEmpty())return m;
        m=clean(model);String b=clean(brand);if(b.isEmpty())b=clean(manufacturer);
        if(m.isEmpty())return b.isEmpty()?"当前设备":b;
        return b.isEmpty()||m.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT))?m:b+" "+m;
    }
    public static String rom(String name,String version){String n=clean(name),v=clean(version);if(n.isEmpty())return "系统名称未公开";return v.isEmpty()?n+" · 版本未公开":n+" "+v;}
}
