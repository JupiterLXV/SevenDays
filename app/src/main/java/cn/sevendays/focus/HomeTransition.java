package cn.sevendays.focus;
/** A bounded transition, not a quota exemption. A new focused app ends it immediately. */
final class HomeTransition {
    // Allow a slow OEM HOME animation to finish, without an unbounded quota exemption.
    static final long MAX_MS=1600;
    private String source="";private long until;
    boolean begin(String pkg,long now){if(!source.isEmpty()&&now<until)return false;source=pkg;until=now+MAX_MS;return true;}
    boolean expired(long now){return !source.isEmpty()&&now>=until;}
    boolean waiting(long now,String focused){if(source.isEmpty())return false;if(now>=until||(focused!=null&&!focused.isEmpty()&&!source.equals(focused))){clear();return false;}return true;}
    String source(){return source;}
    void clear(){source="";until=0;}
}
