package cn.sevendays.focus;

public final class SheetPhysicsTests {
    static int checks;
    static void check(boolean value,String scenario){if(!value)throw new AssertionError(scenario);checks++;}
    public static void main(String[] args){
        for(float density:new float[]{1,2,3}){
            float height=600*density;
            check(!SheetPhysics.dismiss(12*density,0,height,density),"small accidental move returns");
            check(!SheetPhysics.dismiss(18*density,2200*density,height,density),"short jitter does not fling-dismiss");
            check(SheetPhysics.dismiss(40*density,1500*density,height,density),"deliberate quick downward flick dismisses");
            check(SheetPhysics.dismiss(150*density,0,height,density),"long slow pull dismisses");
            check(!SheetPhysics.dismiss(150*density,-900*density,height,density),"reversing upward returns the sheet");
            check(!SheetPhysics.dismiss(-18*density,2000*density,height,density),"upward stretch cannot dismiss");
            check(SheetPhysics.position(-500*density,height,density)>=-18*density,"upward resistance is bounded");
            check(SheetPhysics.position(2000*density,height,density)<=height+24*density,"downward tracking is bounded");
            check(SheetPhysics.dismiss(80*density,0,200*density,density),"short panels remain dismissible");
        }
        System.out.println("PASS "+checks+" native sheet gesture decisions");
    }
}
