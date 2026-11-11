import cn.sevendays.focus.DeviceIdentity;
public final class DeviceIdentityTests {
    static int passed;
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);passed++;}
    public static void main(String[] args){
        check(DeviceIdentity.isVivo("vivo","vivo"),"vivo vendor");
        check(DeviceIdentity.isVivo("vivo","iQOO"),"iqoo brand");
        check(DeviceIdentity.isVivo("IQOO",""),"case insensitive");
        check(!DeviceIdentity.isVivo("Google","google"),"generic guide");
        check(!DeviceIdentity.isVivo(null,null),"missing vendor");
        check(DeviceIdentity.title("vivo","vivo","V2505A","").equals("vivo V2505A"),"do not invent retail name");
        check(DeviceIdentity.title("vivo","vivo","V2505A","iQOO 15").equals("iQOO 15"),"public retail value preferred");
        check(DeviceIdentity.title("vivo","iQOO","iQOO 15","").equals("iQOO 15"),"no repeated brand");
        check(DeviceIdentity.title(null,null,null,null).equals("当前设备"),"safe missing fallback");
        check(DeviceIdentity.rom("","6").equals("系统名称未公开"),"do not infer OriginOS from a number");
        check(DeviceIdentity.rom("OriginOS","").equals("OriginOS · 版本未公开"),"missing ROM version honest");
        check(DeviceIdentity.rom("OriginOS","6").equals("OriginOS 6"),"explicit ROM fields");
        check(DeviceIdentity.clean(" unknown ").isEmpty(),"unknown placeholders");
        check(DeviceIdentity.clean("iQOO\n15").equals("iQOO 15"),"control chars normalized");
        System.out.println("PASS: "+passed+" device identity assertions");
    }
}
