package cn.sevendays.focus;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.*;

/** Worker-only package/bounds reader. Never walks descendants or reads text. */
final class WindowReader {
    static final class Snapshot {
        final List<WindowPolicy.Window> windows;
        final String focused;
        final long capturedAt, duration;
        Snapshot(List<WindowPolicy.Window> w,String f,long at,long ms) {
            windows=Collections.unmodifiableList(w);focused=f;capturedAt=at;duration=ms;
        }
    }
    private final AccessibilityService service;
    private final Set<String> safe;
    // A window ID belongs to one system window. Forget IDs as soon as that window disappears.
    private final Map<Integer,String> packages=new HashMap<>();
    WindowReader(AccessibilityService s,Set<String> safe) { service=s;this.safe=new HashSet<>(safe); }
    Snapshot read() {
        long at=SystemClock.elapsedRealtime();
        List<WindowPolicy.Window> result=new ArrayList<>();String focused="";
        PowerManager power=(PowerManager)service.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)service.getSystemService(Context.KEYGUARD_SERVICE);
        if(power==null||!power.isInteractive()||(keyguard!=null&&keyguard.isKeyguardLocked())) {
            packages.clear();return new Snapshot(result,focused,at,SystemClock.elapsedRealtime()-at);
        }
        Set<Integer> present=new HashSet<>();
        List<AccessibilityWindowInfo> windows=service.getWindows();
        try {
            for(AccessibilityWindowInfo w:windows) {
                if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                if(Build.VERSION.SDK_INT>=30&&w.getDisplayId()!=Display.DEFAULT_DISPLAY)continue;
                int id=w.getId();present.add(id);
                String pkg=packages.get(id);
                if(pkg==null) {
                    // API 33+: requesting just the root avoids the default descendant prefetch.
                    AccessibilityNodeInfo root=Build.VERSION.SDK_INT>=33?w.getRoot(0):w.getRoot();
                    if(root!=null)try {
                        CharSequence name=root.getPackageName();
                        if(name!=null&&!name.toString().isEmpty()){pkg=name.toString();packages.put(id,pkg);}
                    }finally{root.recycle();}
                }
                Rect rect=new Rect();w.getBoundsInScreen(rect);
                if(pkg!=null&&w.isFocused())focused=pkg;
                if(pkg!=null&&!rect.isEmpty())result.add(new WindowPolicy.Window(pkg,
                    new WindowPolicy.Box(rect.left,rect.top,rect.right,rect.bottom),w.getLayer(),safe.contains(pkg)));
            }
        } finally {
            for(AccessibilityWindowInfo w:windows)w.recycle();
            packages.keySet().retainAll(present);
        }
        return new Snapshot(result,focused,at,SystemClock.elapsedRealtime()-at);
    }
}
