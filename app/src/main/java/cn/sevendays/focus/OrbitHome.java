package cn.sevendays.focus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline presentation only. No rule mutation is exposed to JavaScript. */
public final class OrbitHome extends FrameLayout {
    interface Listener { void command(String action, String value); void failed(); }
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private final Map<String,String> icons = new LinkedHashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final ProgressBar progress;
    private WebView web;
    private boolean ready, disposed, modal, paused, themeRequested;
    private String lastSnapshot = "";
    private JSONObject pendingSnapshot;
    private final Runnable timeout = () -> { if (!ready && !disposed) fail(); };

    OrbitHome(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(Ui.BG);
        progress = new ProgressBar(context);
        progress.setContentDescription("正在加载首页");
        LayoutParams lp = new LayoutParams(Ui.dp(context,32), Ui.dp(context,32), Gravity.CENTER);
        addView(progress, lp);
        try {
            web = new WebView(context);
            web.setBackgroundColor(Ui.BG);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setBlockNetworkLoads(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setTextZoom(Math.max(85, Math.min(200, Math.round(context.getResources().getConfiguration().fontScale*100))));
            web.addJavascriptInterface(new Bridge(), "SevenDaysNative");
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    if ("data".equals(uri.getScheme())) return null;
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Offline", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    if(disposed||web!=view)return true;
                    if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
                    web=null;
                    view.destroy();
                    if(!disposed)Diagnostics.event(context,detail.didCrash()?"首页渲染器异常退出":"首页渲染器被系统回收",null);
                    main.post(() -> fail());
                    return true;
                }
            });
            addView(web, 0, new LayoutParams(-1,-1));
            String html;
            try (java.io.InputStream in = context.getAssets().open("orbit/index.html")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer,0,count);
                html = bytes.toString("UTF-8");
            }
            String initialTheme=context.getSharedPreferences("policy_v1",0).getString("orbit_theme","saturn");
            if(!initialTheme.equals("star")&&!initialTheme.equals("aurora"))initialTheme="saturn";
            html=html.replace("__SEVEN_THEME__",initialTheme);
            // The fixed HTTPS origin is only a base; all resources are embedded and network loads are blocked.
            web.loadDataWithBaseURL("https://sevendays.invalid/", html, "text/html", "UTF-8", null);
            main.postDelayed(timeout, 12000);
        } catch (Exception | LinkageError error) {
            Diagnostics.event(context, "星环首页加载失败，使用列表首页", error);
            main.post(() -> fail());
        }
    }

    public final class Bridge {
        @JavascriptInterface public void postMessage(String raw) {
            if (raw == null || raw.length() > 8192) return;
            final String action, value;
            try { JSONObject o = new JSONObject(raw); action = o.getString("action"); value = o.optString("value", ""); }
            catch (JSONException error) { return; }
            main.post(() -> {
                if (disposed) return;
                switch (action) {
                    case "ready":
                        if (!ready) { ready=true; main.removeCallbacks(timeout); progress.setVisibility(GONE); sendIcons(new LinkedHashMap<>(icons)); }
                        if (pendingSnapshot!=null) sendSnapshot(pendingSnapshot);
                        listener.command("ready", "");
                        if (paused) evaluate("window.SevenNativeHome&&window.SevenNativeHome.suspend()");
                        if (themeRequested) { themeRequested=false; themePicker(); }
                        break;
                    case "modal": modal="open".equals(value); break;
                    case "settings": case "add": case "detail": case "theme": case "select": case "sort":
                        if (ready&&!paused) listener.command(action,value);
                        break;
                    case "fallback": fail(); break;
                    default: break;
                }
            });
        }
    }

    void snapshot(JSONObject data) {
        if (disposed) return;
        pendingSnapshot=data;
        JSONArray apps=data.optJSONArray("apps");
        List<String> wanted=new ArrayList<>();
        if (apps!=null) for(int i=0;i<apps.length();i++) {
            String pkg=apps.optJSONObject(i).optString("pkg");
            if (!pkg.isEmpty()&&!icons.containsKey(pkg)&&loading.add(pkg)) wanted.add(pkg);
        }
        if (!wanted.isEmpty()) images.execute(() -> {
            Map<String,String> loaded=new LinkedHashMap<>();
            for(String pkg:wanted) {
                if(Thread.currentThread().isInterrupted()) break;
                try {
                    Drawable d=getContext().getPackageManager().getApplicationIcon(pkg);
                    Bitmap bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(bitmap);d.setBounds(0,0,96,96);d.draw(canvas);
                    ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
                    loaded.put(pkg,Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
                } catch(Exception | LinkageError ignored) { loaded.put(pkg,""); }
            }
            main.post(() -> { if(disposed)return;loading.removeAll(wanted);icons.putAll(loaded);if(ready)sendIcons(loaded); });
        });
        if(ready)sendSnapshot(data);
    }
    private void sendSnapshot(JSONObject data) {
        String raw=data.toString();if(raw.equals(lastSnapshot))return;
        lastSnapshot=raw;send("accept",raw);
    }
    private void sendIcons(Map<String,String> loaded) {
        if(loaded.isEmpty())return;
        JSONObject object=new JSONObject();
        try{for(Map.Entry<String,String> e:loaded.entrySet())object.put(e.getKey(),e.getValue());}catch(JSONException ignored){return;}
        send("icons",object.toString());
    }
    private void send(String method,String raw) {
        String encoded=Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        evaluate("window.SevenNativeHome&&window.SevenNativeHome."+method+"('"+encoded+"')");
    }
    private void evaluate(String code) { if(web!=null&&!disposed)web.evaluateJavascript(code,null); }
    void select(String pkg) { try{send("select",new JSONObject().put("pkg",pkg).toString());}catch(JSONException ignored){} }
    void themePicker() { if(!ready){themeRequested=true;return;}evaluate("window.SevenNativeHome&&window.SevenNativeHome.themes()"); }
    boolean back() { if(!modal)return false;modal=false;evaluate("window.SevenNativeHome&&window.SevenNativeHome.close()");return true; }
    void pause() { paused=true;evaluate("window.SevenNativeHome&&window.SevenNativeHome.suspend()");if(web!=null)web.onPause(); }
    void resume() { paused=false;if(web!=null)web.onResume();evaluate("window.SevenNativeHome&&window.SevenNativeHome.resume()");lastSnapshot=""; }
    private void fail() { if(disposed)return;dispose();listener.failed(); }
    void dispose() {
        if(disposed)return;disposed=true;main.removeCallbacksAndMessages(null);images.shutdownNow();
        if(web!=null){WebView old=web;web=null;removeView(old);old.removeJavascriptInterface("SevenDaysNative");old.stopLoading();old.destroy();}
        icons.clear();loading.clear();pendingSnapshot=null;lastSnapshot="";
    }
}
