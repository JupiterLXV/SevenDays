package cn.sevendays.focus;

import static cn.sevendays.focus.ProtectionRunState.Authorization.*;
import static cn.sevendays.focus.ProtectionRunState.Presentation.CONNECTING;
import static cn.sevendays.focus.ProtectionRunState.Presentation.OFF;
import static cn.sevendays.focus.ProtectionRunState.Presentation.ISSUE;
import static cn.sevendays.focus.ProtectionRunState.Presentation.RUNNING;

public final class BackgroundStabilityTests {
    private static int passed;
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
        passed++;System.out.println("PASS "+message);
    }
    public static void main(String[] args){
        ProtectionRunState service=new ProtectionRunState();
        check(service.keepRunning(true,ENABLED,10),"opted-in authorized service starts");
        check(service.keepRunning(true,ENABLED,7_200_000),"authorization retains service after hours");
        check(service.keepRunning(true,DISABLED,7_200_001),"one late disabled observation cannot stop service");
        check(service.keepRunning(true,DISABLED,7_320_000),"continuous disable grace is not tied to process age");
        check(!service.keepRunning(true,DISABLED,7_320_001),"stable disabled permission stops foreground notice");

        service=new ProtectionRunState();
        check(service.keepRunning(true,DISABLED,0),"initial authorization setup retains grace");
        check(service.keepRunning(true,UNKNOWN,250_000),"a failed permission read never masquerades as denial");
        check(service.keepRunning(true,DISABLED,250_001),"unknown breaks the continuous disabled interval");
        check(service.keepRunning(true,ENABLED,360_000),"authorization recovery clears the disable timer");
        check(service.keepRunning(true,DISABLED,1_000_000),"later temporary disconnection gets a fresh grace interval");
        check(!service.keepRunning(false,ENABLED,1_000_001),"opting out stops even when accessibility stays authorized");
        check(!service.keepRunning(false,UNKNOWN,1_000_002),"unknown permission cannot override opt-out");
        check(ProtectionRunState.presentation(ENABLED,false,false)==CONNECTING,"enabled authorization alone is not protection");
        check(ProtectionRunState.presentation(UNKNOWN,false,false)==ProtectionRunState.Presentation.UNKNOWN,"read failure remains distinct from switching permission off");
        check(ProtectionRunState.presentation(DISABLED,true,false)==OFF,"an explicitly disabled switch is not masked by a recent heartbeat");
        check(ProtectionRunState.presentation(ENABLED,true,true)==ISSUE,"a diagnostic fault stays visible despite a connected service");
        check(ProtectionRunState.presentation(ENABLED,true,false)==RUNNING,"confirmed protection displays the healthy state");

        SnapshotCadence snapshots=new SnapshotCadence();
        check(snapshots.due(0,80,false,false,false),"initial clock snapshot is written");
        snapshots.enqueued(0,80);
        for(long t=1000;t<60_000;t+=1000)if(snapshots.due(t,80,false,false,false))throw new AssertionError("idle write storm at "+t);
        check(snapshots.due(60_000,80,false,false,false),"59 idle seconds skip writes; minute boundary refreshes clock");
        snapshots.enqueued(60_000,80);
        check(!snapshots.due(60_100,80,true,true,false),"new use does not pile up writes behind a slow write");
        check(!snapshots.due(60_999,80,false,false,false),"usage save observes the one-second minimum");
        check(snapshots.due(61_000,80,false,false,false),"last partial usage is saved even after app leaves foreground");
        snapshots.enqueued(61_000,80);
        check(snapshots.due(62_000,81,false,false,false),"midnight rollover is saved promptly while idle");
        snapshots.enqueued(62_000,81);
        check(snapshots.due(63_000,81,false,false,true),"failed disk write retries at active cadence while idle");
        snapshots.enqueued(63_000,81);
        check(!snapshots.due(64_000,81,false,false,false),"healthy idle cadence resumes after successful write");
        System.out.println("PASS "+passed+" background stability checks");
    }
}
