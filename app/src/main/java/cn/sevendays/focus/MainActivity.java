package cn.sevendays.focus;

import android.app.*;
import android.animation.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.content.ClipboardManager;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.*;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private Store store;private DeviceInfo device;
    private OrbitHome orbitHome;private boolean plainHome,uiStarted;private String orbitSelectedPkg="";private Dialog ruleDialog;private Runnable ruleDetailTick;
    private static final long ORBIT_RELEASE_DELAY_MS=1200;
    private LinearLayout page,root,listContainer,alertBox;private ScrollView scroll;private FrameLayout stage;
    private TextView status,totalTime,alertText,alertAction,taskState,noticeState,warningState,deviceTitle,deviceRom,serviceState,batteryState,policyError,diagnosticText;
    private Dialog diagnosticDialog,pickerDialog,batchDialog,editor;private boolean continueToAccessibility;
    private final LinkedHashMap<String,HomeRow> homeRows=new LinkedHashMap<>();private String expandedPkg="",draggingPkg="";private int dragScroll;
    private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int tab;private final int[] scrollPositions={0,0};
    private EditText minuteInput,pickerSearch;private CheckBox lockConsent,batchConsent;private TextView singleDeadline,singleImpact,singleRefill,batchDeadline,batchImpact,batchRefill,batchError;private Button batchSave;
    private String draftPkg="",draftName="",batchExpanded="";private boolean draftNew;
    private List<BatchRules.Entry> batchDraft=new ArrayList<>();private LinkedHashMap<String,BatchRules.Entry> pickerChosen=new LinkedHashMap<>();
    private final Map<String,EditText> batchInputs=new LinkedHashMap<>();private final Map<String,TextView> batchImpacts=new LinkedHashMap<>();
    private final Map<String,FlowUi.Expansion> batchExpansions=new LinkedHashMap<>();
    private final Runnable ticker=new Runnable(){public void run(){updateValues();handler.postDelayed(this,1000);}};
    private final Runnable autoScroll=new Runnable(){public void run(){if(dragScroll!=0&&!draggingPkg.isEmpty()&&scroll!=null){scroll.scrollBy(0,dragScroll);handler.postDelayed(this,40);}}};
    private final Runnable releaseIdleOrbit=()->{if(!uiStarted&&orbitHome!=null){releaseOrbitHome();Diagnostics.orbitReleases++;}};
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.theme(this);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);store=Store.get(this);orbitSelectedPkg=store.prefs.getString("orbit_selected","");device=new DeviceInfo();if(getIntent().getBooleanExtra("open_device",false))tab=1;if(b!=null){continueToAccessibility=b.getBoolean("continueToAccessibility",false);tab=b.getInt("tab",0);scrollPositions[0]=b.getInt("scroll0",0);scrollPositions[1]=b.getInt("scroll1",0);expandedPkg=b.getString("expandedPkg","");orbitSelectedPkg=b.getString("orbitSelectedPkg",orbitSelectedPkg);}render();openRuleIntent(getIntent());worker.execute(()->{device.loadOptional();handler.post(()->{if(!isDestroyed())updateDevice();});});if(b!=null)handler.post(()->restoreDraft(b));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_device",false))switchTab(1);openRuleIntent(intent);}
    private void openRuleIntent(Intent intent){String pkg=intent.getStringExtra("rule_pkg");if(pkg!=null)handler.post(()->ruleOptions(pkg));}
    @Override protected void onStart(){
        super.onStart();uiStarted=true;handler.removeCallbacks(releaseIdleOrbit);
        if(root!=null&&tab==0&&!usesListHome()&&orbitHome==null)attachOrbitHome();
    }
    @Override protected void onResume(){super.onResume();TaskPresentation.apply(this);if(store.prefs.getBoolean("persistent_notice",false)&&Diagnostics.granted(this))ProtectionService.startFromActivity(this);PolicyOps.sync(this,GuardService.alive());PolicyOps.schedule(this);if(orbitHome!=null)orbitHome.resume();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){stopDragScroll();draggingPkg="";if(orbitHome!=null)orbitHome.pause();if(!orbitSelectedPkg.equals(store.prefs.getString("orbit_selected","")))store.prefs.edit().putString("orbit_selected",orbitSelectedPkg).apply();handler.removeCallbacks(ticker);store.now();if(!store.corrupt)try{store.checkpointAsync();}catch(RuntimeException e){Diagnostics.event(this,"页面离开时保存失败",e);}super.onPause();}
    @Override protected void onStop(){
        super.onStop();uiStarted=false;handler.removeCallbacks(releaseIdleOrbit);
        if(orbitHome!=null)handler.postDelayed(releaseIdleOrbit,ORBIT_RELEASE_DELAY_MS);
    }
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(!uiStarted&&level>=ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN){
            handler.removeCallbacks(releaseIdleOrbit);releaseIdleOrbit.run();
        }
    }
    @Override protected void onDestroy(){releaseOrbitHome();for(Dialog d:new Dialog[]{editor,batchDialog,pickerDialog,diagnosticDialog,ruleDialog})if(d!=null&&d.isShowing())d.dismiss();handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putBoolean("continueToAccessibility",continueToAccessibility);rememberScroll();b.putInt("tab",tab);b.putInt("scroll0",scrollPositions[0]);b.putInt("scroll1",scrollPositions[1]);b.putString("expandedPkg",expandedPkg);b.putString("orbitSelectedPkg",orbitSelectedPkg);
        if(pickerDialog!=null&&pickerDialog.isShowing()){b.putString("flow","picker");saveEntries(b,"chosen",new ArrayList<>(pickerChosen.values()));b.putString("query",pickerSearch==null?"":pickerSearch.getText().toString());}
        else if(batchDialog!=null&&batchDialog.isShowing()){b.putString("flow","batch");saveEntries(b,"chosen",batchDraft);b.putBoolean("batchConsent",batchConsent!=null&&batchConsent.isChecked());b.putString("batchExpanded",batchExpanded);}
        else if(editor!=null&&editor.isShowing()){b.putString("flow","single");b.putString("draftPkg",draftPkg);b.putString("draftName",draftName);b.putString("draftText",minuteInput.getText().toString());b.putBoolean("draftConsent",lockConsent.isChecked());b.putBoolean("draftNew",draftNew);}
    }
    private void saveEntries(Bundle b,String prefix,List<BatchRules.Entry> entries){ArrayList<String> packages=new ArrayList<>(),names=new ArrayList<>();int[] minutes=new int[entries.size()];for(int i=0;i<entries.size();i++){BatchRules.Entry e=entries.get(i);packages.add(e.pkg);names.add(e.name);minutes[i]=e.minutes;}b.putStringArrayList(prefix+"Packages",packages);b.putStringArrayList(prefix+"Names",names);b.putIntArray(prefix+"Minutes",minutes);}
    private List<BatchRules.Entry> loadEntries(Bundle b,String prefix){List<BatchRules.Entry> out=new ArrayList<>();ArrayList<String> packages=b.getStringArrayList(prefix+"Packages"),names=b.getStringArrayList(prefix+"Names");int[] minutes=b.getIntArray(prefix+"Minutes");if(packages!=null&&names!=null&&minutes!=null&&packages.size()==names.size()&&packages.size()==minutes.length)for(int i=0;i<packages.size();i++)out.add(new BatchRules.Entry(packages.get(i),names.get(i),minutes[i]));return out;}
    private void restoreDraft(Bundle b){if(isDestroyed())return;String flow=b.getString("flow","");if(flow.equals("picker"))openPicker(loadEntries(b,"chosen"),b.getString("query",""));else if(flow.equals("batch"))showBatch(loadEntries(b,"chosen"),b.getBoolean("batchConsent"),b.getString("batchExpanded",""));else if(flow.equals("single")||!b.getString("draftPkg","").isEmpty()){String pkg=b.getString("draftPkg","");if(!pkg.isEmpty())showEditor(pkg,b.getString("draftName",pkg),b.getString("draftText",String.valueOf(b.getInt("draftMinutes",30))),b.getBoolean("draftConsent"),b.getBoolean("draftNew",!store.book.rules.containsKey(pkg)));}}
    private int dp(int v){return Ui.dp(this,v);}
    private String date(long t){return Instant.ofEpochMilli(t).atZone(ZoneId.of(store.book.zone)).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));}
    private boolean usesListHome(){return plainHome||"list".equals(store.prefs.getString("home_view","orbit"));}
    private void rememberScroll(){if(scroll!=null&&(tab!=0||usesListHome()))scrollPositions[tab]=scroll.getScrollY();}
    private void chooseHomeView(String value){
        if(tab!=0||!("orbit".equals(value)||"list".equals(value)))return;
        boolean list="list".equals(value);
        if(!list&&plainHome){toast("系统渲染组件不可用");return;}
        if(list==usesListHome())return;
        stopDragScroll();rememberScroll();draggingPkg="";
        store.prefs.edit().putString("home_view",value).apply();
        render();
    }
    private void switchTab(int value){if(tab==value)return;stopDragScroll();rememberScroll();tab=value;render();if(MotionUi.enabled(this)){root.setAlpha(.6f);root.setTranslationY(dp(7));root.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(MotionUi.EASE).start();}}
    @Override public void onBackPressed(){if(tab==1){switchTab(0);return;}if(orbitHome!=null){if(orbitHome.back())return;orbitHome.pause();}else if(!expandedPkg.isEmpty()){toggleRow(expandedPkg);return;}super.onBackPressed();}
    private void attachOrbitHome(){
        LinearLayout host=root;host.removeView(scroll);
        orbitHome=new OrbitHome(this,new OrbitHome.Listener(){
            private boolean current(){return !isDestroyed()&&!isFinishing()&&root==host&&tab==0&&orbitHome!=null;}
            public void command(String action,String value){if(current())orbitCommand(action,value);}
            public void failed(){
                if(!current())return;
                plainHome=true;toast("系统渲染组件不可用，已使用列表首页");render();
            }
        });
        host.addView(orbitHome,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void releaseOrbitHome(){
        if(orbitHome==null)return;
        OrbitHome old=orbitHome;orbitHome=null;
        if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
        old.dispose();
    }
    private void render(){
        releaseOrbitHome();Ui.theme(this);if(root!=null)root.animate().cancel();homeRows.clear();taskState=noticeState=warningState=serviceState=batteryState=policyError=deviceTitle=deviceRom=status=totalTime=null;
        if(!store.book.rules.containsKey(expandedPkg))expandedPkg="";
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page=Ui.column(this);page.setPadding(dp(20),dp(18),dp(20),dp(20));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        alertBox=null;listContainer=null;if(tab==0&&!usesListHome())attachOrbitHome();else if(tab==0)renderToday();else renderDevice();
        if(tab==0&&usesListHome()){LinearLayout foot=Ui.row(this);foot.setGravity(Gravity.END);foot.setPadding(dp(20),dp(7),dp(20),dp(12));Button add=Ui.button(this,"＋ 添加应用",true,this::pickApp);add.setContentDescription("批量添加限时应用");add.setMinimumHeight(dp(50));foot.addView(add,new LinearLayout.LayoutParams(-2,-2));root.addView(foot);}
        if(stage==null){stage=new FrameLayout(this);setContentView(stage);}stage.removeAllViews();stage.addView(root,new FrameLayout.LayoutParams(-1,-1));root.requestApplyInsets();ScrollView shown=scroll;int position=scrollPositions[tab];shown.post(()->{if(shown==scroll)shown.scrollTo(0,position);});updateValues();
    }
    private View iconButton(String name,String label,Runnable action){FrameLayout box=new FrameLayout(this);box.setBackground(Ui.ripple(this,Ui.BG,18));Ui.Icon icon=new Ui.Icon(this,name,Ui.GREEN);box.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));box.setContentDescription(label);Ui.clickable(box,()->{MotionUi.tap(box);action.run();});return box;}
    private void renderToday(){
        LinearLayout heading=Ui.row(this);ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);heading.addView(logo,new LinearLayout.LayoutParams(dp(34),dp(34)));Ui.gap(heading,10);TextView name=Ui.bold(this,"SevenDays",23);name.setMaxLines(1);name.setAutoSizeTextTypeUniformWithConfiguration(17,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));heading.addView(iconButton("settings","设备与权限",()->switchTab(1)),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(heading);Ui.space(page,13);
        LinearLayout summary=getResources().getConfiguration().fontScale>1.4f?Ui.column(this):Ui.row(this);LinearLayout time=Ui.row(this);time.addView(Ui.text(this,"今日已用",12,Ui.MUTED));Ui.gap(time,8);totalTime=Ui.bold(this,"0 分钟",17);totalTime.setFontFeatureSettings("tnum");time.addView(totalTime);summary.addView(time,summary.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));if(summary.getOrientation()==LinearLayout.VERTICAL)Ui.space(summary,6);status=Ui.text(this,"",11,Ui.GREEN);status.setMinHeight(dp(44));status.setGravity(Gravity.CENTER_VERTICAL);status.setBackground(Ui.ripple(this,Ui.BG,12));Ui.pad(status,4,0);status.setContentDescription("查看服务状态");Ui.clickable(status,this::openProtectionAction);summary.addView(status);page.addView(summary);Ui.space(page,9);
        alertBox=Ui.row(this);Ui.pad(alertBox,12,8);alertBox.setBackground(Ui.ripple(this,Ui.SOFT,18));LinearLayout alertWords=Ui.column(this);alertText=Ui.bold(this,"",13);alertWords.addView(alertText);Ui.space(alertWords,3);alertAction=Ui.text(this,"",12,Ui.GREEN);alertWords.addView(alertAction);alertBox.addView(alertWords,new LinearLayout.LayoutParams(0,-2,1));Ui.gap(alertBox,10);alertBox.addView(new Ui.Icon(this,"chevron",Ui.GREEN),new LinearLayout.LayoutParams(dp(18),dp(18)));alertBox.setMinimumHeight(dp(52));Ui.clickable(alertBox,this::openProtectionAction);page.addView(alertBox);
        LinearLayout layout=Ui.row(this);layout.setMinimumHeight(dp(48));
        LinearLayout modes=Ui.row(this);modes.setBackground(Ui.shape(Ui.SOFT,dp(16)));Ui.pad(modes,3,2);
        Button orbit=Ui.button(this,"星环",false,()->chooseHomeView("orbit")),list=Ui.button(this,"列表",true,()->{});
        orbit.setEnabled(!plainHome);orbit.setContentDescription(plainHome?"星环暂不可用":"切换到星环首页");
        list.setSelected(true);list.setOnClickListener(null);list.setClickable(false);list.setFocusable(true);list.setContentDescription("列表，当前首页");
        int segmentWidth=getResources().getConfiguration().fontScale>1.4f?66:55;
        for(Button button:new Button[]{orbit,list}){button.setMinimumHeight(dp(48));Ui.pad(button,8,0);modes.addView(button,new LinearLayout.LayoutParams(dp(segmentWidth),-2));}
        layout.addView(modes,new LinearLayout.LayoutParams(-2,-2));layout.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));Ui.gap(layout,10);
        Button theme=Ui.button(this,orbitThemeName()+" ⌄",false,this::nativeThemePicker);
        theme.setContentDescription("外观主题，当前"+orbitThemeName());layout.addView(theme,new LinearLayout.LayoutParams(-2,-2));
        page.addView(layout);Ui.space(page,8);
        LinearLayout title=Ui.row(this);title.addView(Ui.bold(this,"限时应用",17),new LinearLayout.LayoutParams(0,-2,1));if(store.book.rules.size()>1)title.addView(iconButton("grip","调整显示顺序",this::orderSheet),new LinearLayout.LayoutParams(dp(48),dp(48)));else title.setMinimumHeight(dp(44));page.addView(title);Ui.space(page,7);
        listContainer=Ui.column(this);listContainer.setBackground(Ui.shape(Ui.SURFACE,dp(24)));listContainer.setPadding(dp(14),0,dp(14),0);page.addView(listContainer,new LinearLayout.LayoutParams(-1,-2));
        if(store.book.rules.isEmpty()){LinearLayout empty=Ui.card(this);empty.addView(Ui.bold(this,store.corrupt?"规则无法读取":"尚未添加应用",17));Ui.space(empty,10);if(store.corrupt)empty.addView(Ui.button(this,"运行诊断",false,this::diagnosticDetails));listContainer.addView(empty);}
        else for(String pkg:orderedPackages()){HomeRow row=new HomeRow(store.book.rules.get(pkg));homeRows.put(pkg,row);listContainer.addView(row.box,new LinearLayout.LayoutParams(-1,-2));}refreshDividers();
        Ui.space(page,10);
    }
    private final class HomeRow {
        final String pkg;final LinearLayout box,header,details,actions;final FlowUi.Expansion expansion;final FlowUi.RemainingBand band;
        final TextView name,quota,left,used,reset,until;final View divider;boolean wasLocked,actionsReady;
        HomeRow(RuleBook.Rule rule){pkg=rule.pkg;box=Ui.column(MainActivity.this);header=Ui.column(MainActivity.this);header.setPadding(0,dp(15),0,dp(13));header.setMinimumHeight(dp(84));header.setBackground(Ui.ripple(MainActivity.this,Ui.SURFACE,14));LinearLayout line=Ui.row(MainActivity.this);ImageView icon=appIcon(pkg);icon.setContentDescription(rule.name+"，长按拖动排序");icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);line.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));Ui.gap(line,10);LinearLayout words=Ui.column(MainActivity.this);name=Ui.bold(MainActivity.this,rule.name,15);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);Ui.space(words,4);quota=Ui.text(MainActivity.this,"",11,Ui.MUTED);words.addView(quota);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));Ui.gap(line,8);left=Ui.bold(MainActivity.this,"",24);left.setMaxLines(1);left.setFontFeatureSettings("tnum");left.setAutoSizeTextTypeUniformWithConfiguration(16,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);boolean large=getResources().getConfiguration().fontScale>1.4f;left.setGravity(large?Gravity.START:Gravity.END);if(large){header.addView(line);Ui.space(header,8);header.addView(left,new LinearLayout.LayoutParams(-1,-2));}else{line.addView(left,new LinearLayout.LayoutParams(-2,-2));header.addView(line);}Ui.space(header,11);band=new FlowUi.RemainingBand(MainActivity.this);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(3));bp.leftMargin=large?0:dp(46);header.addView(band,bp);box.addView(header);
            details=Ui.column(MainActivity.this);details.setPadding(dp(46),0,0,dp(13));used=detailLine(details,"今日已用");reset=detailLine(details,"额度恢复");until=detailLine(details,"规则可调整");actions=Ui.column(MainActivity.this);details.addView(actions);expansion=new FlowUi.Expansion(MainActivity.this);expansion.addView(details,new FrameLayout.LayoutParams(-1,-2));box.addView(expansion,new LinearLayout.LayoutParams(-1,-2));divider=new View(MainActivity.this);divider.setBackgroundColor(Ui.LINE);box.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));Ui.clickable(header,()->toggleRow(pkg));header.setOnLongClickListener(v->beginDrag(this));icon.setOnLongClickListener(v->beginDrag(this));header.setOnDragListener((v,event)->dragEvent(this,event));
            header.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View h,android.view.accessibility.AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(h,n);n.setClassName(Button.class.getName());List<String> order=orderedPackages();int i=order.indexOf(pkg);if(i>0)n.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_up,"上移"));if(i>=0&&i<order.size()-1)n.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_down,"下移"));}@Override public boolean performAccessibilityAction(View h,int action,Bundle args){int i=orderedPackages().indexOf(pkg);if(action==R.id.action_move_up)return moveRow(pkg,i-1);if(action==R.id.action_move_down)return moveRow(pkg,i+1);return super.performAccessibilityAction(h,action,args);}});
            expansion.open(pkg.equals(expandedPkg),false);expansion.settled=()->{if(pkg.equals(expandedPkg))ensureVisible(this);};update(store.now());
        }
        void update(long now){RuleBook.Rule r=store.book.rules.get(pkg);if(r==null)return;long rest=store.book.remaining(pkg);boolean locked=store.book.locked(pkg,now),near=r.minutes>0&&rest>0&&rest<=300000;quota.setText(r.minutes==0?"全天禁用":"每天 "+r.minutes+" 分钟");left.setText(r.minutes==0?"全天禁用":rest==0?"已到限":Ui.compact(rest));left.setTextColor(near?Ui.AMBER:rest==0?Ui.MUTED:Ui.INK);band.update(r.minutes==0?0:rest/(r.minutes*60000f),near);used.setText(Ui.duration(store.book.used(pkg)));reset.setText(r.minutes==0?"不自动恢复":date(store.book.nextMidnight(now)));until.setText(locked?date(r.lockedUntil):"现在可调整");header.setContentDescription(r.name+"，"+(r.minutes==0?"全天禁用":rest==0?"今日已到限":"剩余 "+Ui.duration(rest))+"，"+(pkg.equals(expandedPkg)?"收起详情":"展开详情"));header.setSelected(pkg.equals(expandedPkg));if(!actionsReady||wasLocked!=locked){actionsReady=true;wasLocked=locked;actions.removeAllViews();if(!locked){Ui.space(actions,7);actions.addView(Ui.button(MainActivity.this,"调整每日额度",false,()->edit(pkg,r.name,r.minutes,false)),new LinearLayout.LayoutParams(-1,-2));Ui.space(actions,4);actions.addView(Ui.button(MainActivity.this,"删除规则",false,()->confirmDelete(pkg)),new LinearLayout.LayoutParams(-1,-2));}}}
    }
    private TextView detailLine(LinearLayout parent,String label){boolean large=getResources().getConfiguration().fontScale>1.4f;LinearLayout line=large?Ui.column(this):Ui.row(this);TextView k=Ui.text(this,label,11,Ui.MUTED);line.addView(k,large?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));if(large)Ui.space(line,3);TextView v=Ui.text(this,"",11,Ui.INK);v.setGravity(large?Gravity.START:Gravity.END);line.addView(v);parent.addView(line);Ui.space(parent,7);return v;}
    private void toggleRow(String pkg){HomeRow target=homeRows.get(pkg);if(target==null)return;MotionUi.tap(target.header);String old=expandedPkg;expandedPkg=pkg.equals(old)?"":pkg;if(!old.isEmpty()&&homeRows.containsKey(old)){HomeRow r=homeRows.get(old);r.expansion.open(false,true);r.update(store.now());}if(!expandedPkg.isEmpty()){orbitSelectedPkg=pkg;target.expansion.open(true,true);target.update(store.now());}}
    private void ensureVisible(HomeRow row){if(listContainer==null||row.box.getParent()!=listContainer)return;row.box.post(()->{if(isDestroyed()||row.box.getParent()!=listContainer||!row.pkg.equals(expandedPkg))return;android.graphics.Rect rect=new android.graphics.Rect(0,0,row.box.getWidth(),row.box.getHeight());row.box.requestRectangleOnScreen(rect,false);});}
    private void confirmDelete(String pkg){RuleBook.Rule r=store.book.rules.get(pkg);if(r==null)return;new AlertDialog.Builder(this).setTitle("删除 "+r.name+" 的规则？").setMessage("当日用量保留。").setPositiveButton("删除",(d,w)->mutate(book->book.remove(pkg,store.now()))).setNegativeButton("取消",null).show();}
    private List<String> orderedPackages(){List<String> saved=new ArrayList<>();try{org.json.JSONArray a=new org.json.JSONArray(store.prefs.getString("ui_app_order","[]"));for(int i=0;i<a.length();i++)saved.add(a.optString(i));}catch(RuntimeException|org.json.JSONException ignored){}return DisplayOrder.reconcile(store.book.rules.keySet(),saved);}
    private boolean moveRow(String pkg,int target){List<String> order=orderedPackages();if(!order.contains(pkg)||target<0||target>=order.size())return false;List<String> moved=DisplayOrder.move(order,pkg,target);if(moved.equals(order))return true;try{if(!store.prefs.edit().putString("ui_app_order",new org.json.JSONArray(moved).toString()).commit()){toast("显示顺序保存失败");return false;}}catch(RuntimeException e){toast("显示顺序保存失败");return false;}
        if(listContainer!=null){Map<View,Integer> tops=new HashMap<>();for(int i=0;i<listContainer.getChildCount();i++){View v=listContainer.getChildAt(i);tops.put(v,v.getTop());}HomeRow row=homeRows.get(pkg);if(row!=null){listContainer.removeView(row.box);listContainer.addView(row.box,target);}refreshDividers();LinearLayout targetList=listContainer;targetList.post(()->{if(targetList!=listContainer||!targetList.isAttachedToWindow()||!MotionUi.enabled(this))return;for(int i=0;i<targetList.getChildCount();i++){View v=targetList.getChildAt(i);Integer old=tops.get(v);if(old!=null){v.animate().cancel();v.setTranslationY(old-v.getTop());v.animate().translationY(0).setDuration(180).setInterpolator(MotionUi.EASE).start();}}});}updateOrbit(store.now());MotionUi.tap(root);root.announceForAccessibility("已移到第 "+(target+1)+" 项");return true;
    }
    private void refreshDividers(){if(listContainer==null)return;for(int i=0;i<listContainer.getChildCount();i++){View v=listContainer.getChildAt(i);for(HomeRow row:homeRows.values())if(row.box==v)row.divider.setVisibility(i==listContainer.getChildCount()-1?View.GONE:View.VISIBLE);}}
    private boolean beginDrag(HomeRow row){if(homeRows.size()<2)return false;draggingPkg=row.pkg;MotionUi.tap(row.header);boolean started=row.header.startDragAndDrop(ClipData.newPlainText("应用顺序",row.pkg),new View.DragShadowBuilder(row.header),row.pkg,0);if(started)row.header.setAlpha(.45f);else draggingPkg="";return started;}
    private boolean dragEvent(HomeRow row,DragEvent event){Object local=event.getLocalState();if(!(local instanceof String)||!homeRows.containsKey((String)local))return false;switch(event.getAction()){
        case DragEvent.ACTION_DRAG_STARTED:return true;
        case DragEvent.ACTION_DRAG_ENTERED:row.header.setBackground(Ui.shape(Ui.SOFT,dp(14)));return true;
        case DragEvent.ACTION_DRAG_LOCATION:int[] at=new int[2],viewport=new int[2];row.header.getLocationOnScreen(at);scroll.getLocationOnScreen(viewport);float y=at[1]+event.getY()-viewport[1];dragScroll=y<dp(60)?-dp(12):y>scroll.getHeight()-dp(60)?dp(12):0;handler.removeCallbacks(autoScroll);if(dragScroll!=0)handler.post(autoScroll);return true;
        case DragEvent.ACTION_DRAG_EXITED:row.header.setBackground(Ui.ripple(this,Ui.SURFACE,14));return true;
        case DragEvent.ACTION_DROP:stopDragScroll();List<String> order=orderedPackages();String source=(String)local;int from=order.indexOf(source),to=order.indexOf(row.pkg);if(from==to)return true;boolean after=event.getY()>row.header.getHeight()/2f;int target=to+(after?1:0);if(from<target)target--;return moveRow(source,Math.max(0,Math.min(order.size()-1,target)));
        case DragEvent.ACTION_DRAG_ENDED:stopDragScroll();draggingPkg="";for(HomeRow r:homeRows.values()){r.header.setAlpha(1);r.header.setBackground(Ui.ripple(this,Ui.SURFACE,14));}return true;
        default:return true;
    }}
    private void stopDragScroll(){dragScroll=0;handler.removeCallbacks(autoScroll);}
    private void orderSheet(){LinearLayout body=Ui.column(this);final Dialog[] d=new Dialog[1];Runnable[] fill=new Runnable[1];fill[0]=()->{body.removeAllViews();List<String> order=orderedPackages();for(int i=0;i<order.size();i++){String pkg=order.get(i);RuleBook.Rule r=store.book.rules.get(pkg);LinearLayout line=Ui.row(this);line.addView(appIcon(pkg),new LinearLayout.LayoutParams(dp(30),dp(30)));Ui.gap(line,10);line.addView(Ui.text(this,r.name,14,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));final int at=i;Button up=Ui.button(this,"↑",false,()->{if(moveRow(pkg,at-1))fill[0].run();}),down=Ui.button(this,"↓",false,()->{if(moveRow(pkg,at+1))fill[0].run();});up.setContentDescription("上移 "+r.name);down.setContentDescription("下移 "+r.name);up.setEnabled(i>0);down.setEnabled(i<order.size()-1);line.addView(up,new LinearLayout.LayoutParams(dp(48),dp(48)));line.addView(down,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(line);Ui.space(body,8);}};fill[0].run();d[0]=sheet("显示顺序",body);}
    private ImageView appIcon(String pkg){ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(Exception ignored){icon.setImageResource(R.drawable.ic_launcher);}icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return icon;}
    private void renderDevice(){
        LinearLayout heading=Ui.row(this);heading.addView(iconButton("back","返回首页",()->switchTab(0)),new LinearLayout.LayoutParams(dp(48),dp(48)));Ui.gap(heading,6);heading.addView(Ui.bold(this,"设备与权限",23));page.addView(heading);Ui.space(page,18);
        LinearLayout info=Ui.card(this);LinearLayout line=Ui.row(this);LinearLayout mark=Ui.column(this);mark.setGravity(Gravity.CENTER);mark.setBackground(Ui.shape(Ui.SOFT,dp(14)));mark.addView(new Ui.Icon(this,"phone",Ui.GREEN),new LinearLayout.LayoutParams(dp(26),dp(26)));line.addView(mark,new LinearLayout.LayoutParams(dp(48),-2));Ui.gap(line,12);LinearLayout titles=Ui.column(this);deviceTitle=Ui.bold(this,device.title(),19);titles.addView(deviceTitle);Ui.space(titles,6);titles.addView(Ui.text(this,"Android "+device.android,12,Ui.MUTED));line.addView(titles,new LinearLayout.LayoutParams(0,-2,1));info.addView(line);Ui.space(info,17);Ui.divider(info);Ui.space(info,13);deviceRom=Ui.text(this,device.rom(),12,Ui.MUTED);info.addView(deviceRom);Ui.space(info,7);info.addView(Ui.text(this,"系统型号  "+device.model,12,Ui.MUTED));Ui.space(info,7);info.addView(Ui.text(this,"安全补丁  "+(device.patch.isEmpty()?"未公开":device.patch),12,Ui.MUTED));Ui.space(info,14);info.addView(Ui.button(this,"设备信息",false,this::deviceDetails));page.addView(info);Ui.space(page,22);
        page.addView(Ui.bold(this,"运行状态",18));Ui.space(page,11);LinearLayout checks=Ui.card(this);serviceState=settingRow(checks,"shield","无障碍服务","",this::permissions);Ui.divider(checks);noticeState=settingRow(checks,"shield","持续保护通知","",this::notificationGuide);Ui.divider(checks);taskState=settingRow(checks,"phone","最近任务卡片","",this::taskGuide);Ui.divider(checks);warningState=settingRow(checks,"clock","额度即将用完提醒","",this::warningGuide);Ui.divider(checks);batteryState=settingRow(checks,"clock","系统电池优化","",this::batteryGuide);Ui.divider(checks);settingRow(checks,"phone",device.vivo?"iQOO / vivo 后台设置":"后台与自启动","需手动确认",this::vendorGuide);Ui.divider(checks);settingRow(checks,"clock","运行诊断","服务事件与退出记录",this::diagnosticDetails);page.addView(checks);Ui.space(page,22);
        LinearLayout appearance=Ui.card(this);settingRow(appearance,"grid","外观主题",orbitThemeName(),()->{switchTab(0);if(orbitHome!=null)orbitHome.themePicker();else nativeThemePicker();});page.addView(appearance);Ui.space(page,22);
        LinearLayout protection=Ui.card(this);protection.addView(Ui.bold(this,"保护模式",16));Ui.space(protection,8);protection.addView(Ui.text(this,PolicyOps.enabled(this)?"增强模式已启用。":"普通模式可在系统中关闭、清除数据或卸载。",12,Ui.MUTED));Ui.space(protection,13);protection.addView(Ui.button(this,"模式说明",false,this::help));page.addView(protection);Ui.space(page,16);
        policyError=Ui.text(this,"",12,Ui.AMBER);page.addView(policyError);Ui.space(page,10);TextView footer=Ui.text(this,DeviceInfo.appTitle(this),11,Ui.MUTED);footer.setGravity(Gravity.CENTER);page.addView(footer);
    }
    private TextView settingRow(LinearLayout parent,String icon,String label,String value,Runnable action){LinearLayout line=Ui.row(this);line.setMinimumHeight(dp(66));line.addView(new Ui.Icon(this,icon,Ui.GREEN),new LinearLayout.LayoutParams(dp(20),dp(20)));Ui.gap(line,12);LinearLayout words=Ui.column(this);words.addView(Ui.bold(this,label,14));Ui.space(words,5);TextView state=Ui.text(this,value,11,Ui.MUTED);words.addView(state);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));line.addView(new Ui.Icon(this,"chevron",Ui.MUTED),new LinearLayout.LayoutParams(dp(16),dp(16)));line.setBackground(Ui.ripple(this,Ui.SURFACE,12));Ui.clickable(line,action);parent.addView(line);return state;}
    private void updateDevice(){if(deviceTitle!=null)deviceTitle.setText(device.title());if(deviceRom!=null)deviceRom.setText(device.rom());}
    private boolean batteryExempt(){return ((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());}
    private ProtectionRunState.Presentation protection(){
        return ProtectionRunState.presentation(Diagnostics.authorization(this),GuardService.alive(),
            store.corrupt||!store.writeError.isEmpty()||!PolicyOps.error.isEmpty()||GuardService.hasFault());
    }
    private String protectionMessage(ProtectionRunState.Presentation state){
        if(store.corrupt)return "规则读取异常，原数据已保留";
        if(!store.writeError.isEmpty())return store.writeError;
        if(!PolicyOps.error.isEmpty())return PolicyOps.error;
        return state.message;
    }
    private void openProtectionAction(){
        switch(protection()){
            case RUNNING:switchTab(1);break;
            case OFF:permissions();break;
            case UNKNOWN:openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));break;
            case CONNECTING:GuardService.refresh();diagnosticDetails();break;
            case ISSUE:diagnosticDetails();break;
        }
    }
    private static void label(TextView view,String text){
        if(view!=null&&!TextUtils.equals(view.getText(),text))view.setText(text);
    }
    private void updateValues(){if(store==null)return;long now=store.now();QuotaWarnings.update(this,store.book);for(HomeRow r:new ArrayList<>(homeRows.values()))r.update(now);long sum=0;for(String pkg:store.book.rules.keySet())sum+=store.book.used(pkg);if(totalTime!=null)totalTime.setText(Ui.used(sum));ProtectionRunState.Presentation protection=protection();
        boolean running=protection==ProtectionRunState.Presentation.RUNNING;
        if(status!=null){label(status,"● "+protection.label);status.setTextColor(running?Ui.GREEN:Ui.AMBER);status.setContentDescription(protection.label+"，"+protection.action);}
        if(alertBox!=null){
            boolean warning=protection==ProtectionRunState.Presentation.ISSUE||(!running&&!store.book.rules.isEmpty());
            alertBox.setVisibility(warning?View.VISIBLE:View.GONE);
            if(status!=null)status.setVisibility(warning?View.GONE:View.VISIBLE);
            String message=protectionMessage(protection);
            label(alertText,message);label(alertAction,protection.action);alertBox.setContentDescription(message+"，"+protection.action);
        }
        if(serviceState!=null)serviceState.setText(GuardService.status(this));if(taskState!=null)taskState.setText(TaskPresentation.excluded(this)?"隐藏":"显示");if(noticeState!=null)noticeState.setText(ProtectionService.running?(ProtectionService.notificationVisible(this)?"已运行":"服务已运行 · 系统通知未允许"):store.prefs.getBoolean("persistent_notice",false)?"已选择 · 当前未运行":"未启用");if(warningState!=null)warningState.setText(store.prefs.getBoolean("quota_warning",false)?QuotaWarnings.visible(this)?"已启用 · 剩余 5 / 1 分钟提醒":"已选择 · 系统通知未允许":"未启用");if(batteryState!=null)batteryState.setText(batteryExempt()?"已忽略系统电池优化":"尚未忽略 · 查看设置");if(policyError!=null){String error=store.corrupt?"规则读取异常，原数据已保留":!store.writeError.isEmpty()?store.writeError:PolicyOps.error;policyError.setText(error);policyError.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);}if(diagnosticDialog!=null&&diagnosticDialog.isShowing()&&diagnosticText!=null)diagnosticText.setText(Diagnostics.report(this,device));updateDraftPreview(now);updateDevice();if(ruleDetailTick!=null)ruleDetailTick.run();updateOrbit(now,protection);}
    private Dialog sheet(String title,LinearLayout body){return sheet(title,body,null);}
    private Dialog sheet(String title,LinearLayout body,View footer){return sheet(title,body,null,footer);}
    private Dialog sheet(String title,LinearLayout body,View top,View footer){
        DragSheetDialog d=new DragSheetDialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=new DragSheetDialog.Panel(this);box.setBackground(Ui.shape(Ui.BG,dp(26)));box.setPadding(dp(20),0,dp(20),dp(20));
        FrameLayout grip=new FrameLayout(this);grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);grip.setBackground(Ui.ripple(this,Ui.BG,22));View handle=new View(this);handle.setBackground(Ui.shape(Ui.LINE,dp(3)));grip.addView(handle,new FrameLayout.LayoutParams(dp(36),dp(4),Gravity.CENTER));box.addView(grip,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout header=Ui.row(this);TextView sheetTitle=Ui.bold(this,title,21);sheetTitle.setMaxLines(1);sheetTitle.setAutoSizeTextTypeUniformWithConfiguration(14,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);header.addView(sheetTitle,new LinearLayout.LayoutParams(0,-2,1));FrameLayout close=new FrameLayout(this);Ui.Icon x=new Ui.Icon(this,"close",Ui.MUTED);FrameLayout.LayoutParams xi=new FrameLayout.LayoutParams(dp(19),dp(19),Gravity.CENTER);close.addView(x,xi);close.setBackground(Ui.ripple(this,Ui.BG,24));close.setContentDescription("关闭");Ui.clickable(close,()->d.close(false));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(header);Ui.space(box,9);if(top!=null){box.addView(top,new LinearLayout.LayoutParams(-1,dp(100)));Ui.space(box,12);}
        ScrollView sv=new ScrollView(this);sv.setFillViewport(false);sv.setVerticalScrollBarEnabled(false);sv.addView(body);box.addView(sv,new LinearLayout.LayoutParams(-1,-2,1));if(footer!=null){Ui.space(box,6);box.addView(footer,new LinearLayout.LayoutParams(-1,-2));}d.setContentView(box);d.bind(box,grip,header);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.24f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setGravity(Gravity.BOTTOM);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
        d.show();if(w!=null)w.setWindowAnimations(0);if(MotionUi.enabled(this)){box.setTranslationY(dp(18));box.setAlpha(.4f);box.animate().translationY(0).alpha(1).setDuration(200).setInterpolator(MotionUi.EASE).start();}if(w!=null)w.setLayout(-1,-2);return d;
    }
    private void deviceDetails(){LinearLayout body=Ui.column(this);TextView report=Ui.text(this,device.report(this),13,Ui.INK);report.setTextIsSelectable(true);body.addView(report);Ui.space(body,15);body.addView(Ui.text(this,"手机可能只公开 VxxxxA 等系统型号，而不公开零售名称；OriginOS 等定制系统版本也可能无法读取。这里保留真实值，不猜测型号或系统版本。",12,Ui.MUTED));Ui.space(body,18);body.addView(Ui.button(this,"复制这些信息",true,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SevenDays设备信息",device.report(this)));toast("设备信息已复制");}));sheet("设备信息",body);}
    private void openSettings(Intent intent){try{startActivity(intent);}catch(RuntimeException e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));toast("该入口不可用，已打开应用信息");}catch(RuntimeException ignored){toast("请手动打开系统设置");}}}
    private void permissions(){
        LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"无障碍服务用于识别前台应用、累计时长并显示到限遮挡。不读取聊天、输入内容或截图，不上传数据。",14,Ui.INK));Ui.space(body,16);
        body.addView(Ui.text(this,"开启「SevenDays」主开关即可，无需开启快捷方式。",13,Ui.MUTED));Ui.space(body,12);
        body.addView(Ui.text(this,"系统提示“受限设置”时，入口在本应用信息页。连接异常或开关自动关闭时，可在“运行诊断”复制记录。",12,Ui.MUTED));Ui.space(body,15);
        CheckBox persistent=new CheckBox(this);persistent.setText("同时启用持续保护通知");persistent.setTextSize(13);persistent.setTextColor(Ui.INK);persistent.setChecked(store.prefs.getBoolean("persistent_notice",true));persistent.setMinHeight(dp(48));body.addView(persistent);
        body.addView(Ui.text(this,"持续显示服务状态。系统仍可能结束后台进程。",12,Ui.MUTED));Ui.space(body,18);
        final Dialog[] ref=new Dialog[1];body.addView(Ui.button(this,"同意并前往无障碍设置",true,()->{ref[0].dismiss();store.prefs.edit().putBoolean("persistent_notice",persistent.isChecked()).apply();if(persistent.isChecked())enableNotice(true);else{stopService(new Intent(this,ProtectionService.class));openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}}));Ui.space(body,10);
        body.addView(Ui.button(this,"打开应用信息",false,()->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));ref[0]=sheet("计时与拦截授权",body);
    }
    private void enableNotice(boolean goToSettings){
        ProtectionService.startFromActivity(this);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){continueToAccessibility=goToSettings;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7010);}else if(goToSettings)openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7010){boolean go=continueToAccessibility;continueToAccessibility=false;if(go)openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}}
    private void notificationGuide(){
        LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"通过持续通知显示无障碍连接状态，并启用前台服务。",14,Ui.INK));Ui.space(body,14);
        body.addView(Ui.text(this,"自启动及厂商后台设置需手动配置。停止通知不修改规则。",13,Ui.MUTED));Ui.space(body,20);
        final Dialog[] ref=new Dialog[1];body.addView(Ui.button(this,"启用 / 重新启动通知",true,()->{store.prefs.edit().putBoolean("persistent_notice",true).apply();ref[0].dismiss();enableNotice(false);}));Ui.space(body,10);
        body.addView(Ui.button(this,"系统通知设置",false,()->openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))));Ui.space(body,10);
        body.addView(Ui.button(this,"停止持续通知",false,()->{store.prefs.edit().putBoolean("persistent_notice",false).apply();stopService(new Intent(this,ProtectionService.class));ref[0].dismiss();updateValues();}));ref[0]=sheet("持续保护通知",body);
    }
    private void taskGuide(){LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"最近任务卡片控制设置页面的显示，不影响桌面图标。计时与拦截由无障碍服务负责。",14,Ui.INK));Ui.space(body,14);body.addView(Ui.text(this,"隐藏卡片可减少误清理，不修改权限或已保存规则。",13,Ui.MUTED));Ui.space(body,12);body.addView(Ui.text(this,"系统一键清理或强行停止仍可能中断服务。",12,Ui.MUTED));Ui.space(body,20);
        final Dialog[] dialog=new Dialog[1];body.addView(Ui.button(this,"不显示最近任务卡片",true,()->{setTaskCard(true);dialog[0].dismiss();}));Ui.space(body,10);body.addView(Ui.button(this,"保留最近任务卡片",false,()->{setTaskCard(false);dialog[0].dismiss();}));Ui.space(body,10);body.addView(Ui.button(this,"收起界面，继续保护",false,()->{if(!GuardService.alive()){toast("保护尚未正常运行，请先检查无障碍");return;}dialog[0].dismiss();finishAndRemoveTask();}));dialog[0]=sheet("页面与后台保护",body);
    }
    private void setTaskCard(boolean excluded){store.prefs.edit().putBoolean("exclude_task_card",excluded).apply();boolean applied=TaskPresentation.apply(this);updateValues();toast(applied?(excluded?"任务卡片已收起，桌面图标仍保留":"最近任务将保留SevenDays卡片"):"系统暂未确认卡片设置，请重新打开SevenDays检查");}
    private void diagnosticDetails(){
        LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"这里仅记录本应用的服务连接、检查失败和系统进程退出原因，不记录聊天、屏幕内容或使用了哪些应用。",12,Ui.MUTED));Ui.space(body,16);
        TextView text=Ui.text(this,Diagnostics.report(this,device),12,Ui.INK);diagnosticText=text;text.setTextIsSelectable(true);body.addView(text);Ui.space(body,20);
        body.addView(Ui.button(this,"复制诊断信息",true,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SevenDays运行诊断",Diagnostics.report(this,device)));toast("已复制，可粘贴反馈问题");}));Dialog d=sheet("运行诊断",body);diagnosticDialog=d;d.setOnDismissListener(x->{if(diagnosticDialog==d){diagnosticDialog=null;diagnosticText=null;}});
    }
    private void batteryGuide(){LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"系统电池优化可能影响后台保护。请在电池优化列表中找到「SevenDays」，按系统提供的选项允许后台运行或不优化。",14,Ui.INK));Ui.space(body,14);body.addView(Ui.text(this,device.vivo?"iQOO / vivo 还需要检查“后台耗电管理”和“自启动”。本页的系统优化状态不代表这些厂商开关已经打开。":"厂商的省电管理可能有独立的后台限制，请同时检查。",13,Ui.MUTED));Ui.space(body,20);body.addView(Ui.button(this,"前往系统电池优化",true,()->openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))));sheet("后台运行",body);}
    private void vendorGuide(){LinearLayout body=Ui.column(this);String title=device.vivo?"iQOO / vivo 设置引导":"后台设置引导";
        body.addView(Ui.text(this,device.vivo?"根据系统返回的厂商信息，已选择 iQOO / vivo 引导。不同 OriginOS 版本的菜单名称可能不同，可在系统设置中搜索下面的关键词。":"请在系统设置中查找下面的项目，具体菜单名称由手机厂商决定。",13,Ui.MUTED));Ui.space(body,17);
        guideStep(body,"01","自启动",device.vivo?"搜索“自启动”，允许「SevenDays」启动。vivo 常见路径：应用与权限 → 权限管理 → 自启动。":"在系统设置中搜索“自启动”或“应用启动管理”，允许「SevenDays」启动。");guideStep(body,"02","后台耗电管理","搜索“后台耗电管理”或“后台高耗电”，为「SevenDays」选择允许后台运行的选项。");guideStep(body,"03","返回检查","返回设备页，确认无障碍服务显示“已连接”。自启动和厂商后台开关无法由普通应用自动核实。");
        body.addView(Ui.button(this,"打开系统设置",true,()->openSettings(new Intent(Settings.ACTION_SETTINGS))));Ui.space(body,10);body.addView(Ui.button(this,"打开本应用信息",false,()->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));sheet(title,body);
    }
    private void guideStep(LinearLayout body,String n,String title,String detail){LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);row.addView(Ui.chip(this,n,Ui.GREEN,Ui.SOFT));Ui.gap(row,12);LinearLayout words=Ui.column(this);words.addView(Ui.bold(this,title,15));Ui.space(words,7);words.addView(Ui.text(this,detail,12,Ui.MUTED));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);Ui.space(body,21);}
    private static final Set<String> KNOWN=new HashSet<>(Arrays.asList("tv.danmaku.bili","com.bilibili.app.in","com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.lite","com.taobao.taobao","com.xunmeng.pinduoduo","com.twitter.android","com.xingin.xhs"));
    private static final class App {final String pkg,label;App(String p,String l){pkg=p;label=l;}}
    private void pickApp(){if(store.corrupt||!store.writeError.isEmpty()){diagnosticDetails();return;}if(!GuardService.alive()){openProtectionAction();return;}openPicker(new ArrayList<>(),"");}
    private void openPicker(List<BatchRules.Entry> selected,String initialQuery){
        if(pickerDialog!=null)pickerDialog.dismiss();pickerChosen=new LinkedHashMap<>();for(BatchRules.Entry e:selected)if(!store.book.rules.containsKey(e.pkg))pickerChosen.put(e.pkg,e.copy());
        LinearLayout body=Ui.column(this),footer=Ui.column(this);EditText search=new EditText(this);search.setSingleLine();search.setTextSize(14);search.setHint("搜索应用");search.setText(initialQuery);Ui.pad(search,14,12);search.setBackground(Ui.shape(Ui.SURFACE,dp(16)));search.setMinimumHeight(dp(48));body.addView(search,new LinearLayout.LayoutParams(-1,-2));Ui.space(body,10);TextView count=Ui.text(this,"正在读取应用…",12,Ui.MUTED);body.addView(count);Ui.space(body,5);ListView list=new ListView(this);list.setId(11001);list.setDivider(null);list.setNestedScrollingEnabled(true);body.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        Button next=Ui.button(this,"设置额度",true,()->{});next.setId(11000);footer.addView(next,new LinearLayout.LayoutParams(-1,-2));List<App> all=new ArrayList<>(),shown=new ArrayList<>();Runnable update=()->{next.setText("设置额度（"+pickerChosen.size()+"）");next.setEnabled(!pickerChosen.isEmpty());};
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int pos){return shown.get(pos);}public long getItemId(int pos){return pos;}public View getView(int pos,View recycled,ViewGroup parent){App a=shown.get(pos);boolean existing=store.book.rules.containsKey(a.pkg);LinearLayout row=Ui.row(MainActivity.this);row.setPadding(dp(4),dp(8),dp(4),dp(8));row.setMinimumHeight(dp(64));row.addView(appIcon(a.pkg),new LinearLayout.LayoutParams(dp(34),dp(34)));Ui.gap(row,10);TextView title=Ui.bold(MainActivity.this,a.label,14);title.setMaxLines(2);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));CheckBox box=new CheckBox(MainActivity.this);box.setContentDescription(a.label+(existing?"，已有规则":""));box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));box.setMinWidth(dp(48));box.setMinHeight(dp(48));box.setFocusable(false);box.setChecked(pickerChosen.containsKey(a.pkg));box.setEnabled(!existing);box.setOnCheckedChangeListener((v,checked)->{if(checked)pickerChosen.putIfAbsent(a.pkg,new BatchRules.Entry(a.pkg,a.label,30));else pickerChosen.remove(a.pkg);update.run();});row.addView(box);row.setAlpha(existing?.45f:1f);row.setContentDescription(a.label+(existing?"，已添加":pickerChosen.containsKey(a.pkg)?"，已选":"，未选"));return row;}};
        list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->{App a=shown.get(pos);if(store.book.rules.containsKey(a.pkg))return;if(pickerChosen.containsKey(a.pkg))pickerChosen.remove(a.pkg);else pickerChosen.put(a.pkg,new BatchRules.Entry(a.pkg,a.label,30));MotionUi.tap(v);adapter.notifyDataSetChanged();update.run();});
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);shown.clear();for(App a:all)if((a.label+" "+a.pkg).toLowerCase(Locale.ROOT).contains(q))shown.add(a);count.setText(shown.isEmpty()?"没有匹配的应用":"共 "+shown.size()+" 个应用");adapter.notifyDataSetChanged();};search.addTextChangedListener(watcher(filter));
        Dialog d=sheet("选择应用",body,footer);pickerDialog=d;pickerSearch=search;next.setOnClickListener(v->{if(pickerChosen.isEmpty())return;List<BatchRules.Entry> entries=BatchRules.copy(new ArrayList<>(pickerChosen.values()));d.dismiss();if(entries.size()==1){BatchRules.Entry e=entries.get(0);showEditor(e.pkg,e.name,String.valueOf(e.minutes),false,true);}else showBatch(entries,false,"");});d.setOnDismissListener(x->{if(pickerDialog==d){pickerDialog=null;pickerSearch=null;pickerChosen=new LinkedHashMap<>();}});update.run();body.setFocusableInTouchMode(true);body.requestFocus();
        worker.execute(()->{List<App> loaded=new ArrayList<>();String error="";try{PackageManager pm=getPackageManager();ResolveInfo home=pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);String homePkg=home==null?"":home.activityInfo.packageName;TelecomManager tm=(TelecomManager)getSystemService(TELECOM_SERVICE);String dialer=tm==null?"":tm.getDefaultDialerPackage();Set<String> seen=new HashSet<>();for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){ApplicationInfo ai=ri.activityInfo.applicationInfo;String pkg=ai.packageName;if(pkg.equals(getPackageName())||pkg.equals(homePkg)||pkg.equals(dialer)||pkg.equals("com.android.settings")||!seen.add(pkg))continue;if((ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&!KNOWN.contains(pkg))continue;loaded.add(new App(pkg,ri.loadLabel(pm).toString()));}loaded.sort((a,b)->{int ka=KNOWN.contains(a.pkg)?0:1,kb=KNOWN.contains(b.pkg)?0:1;return ka!=kb?Integer.compare(ka,kb):a.label.compareToIgnoreCase(b.label);});}catch(RuntimeException e){error="应用读取失败";}String message=error;handler.post(()->{if(isDestroyed()||!d.isShowing())return;all.addAll(loaded);filter.run();if(!message.isEmpty())count.setText(message);});});
    }
    private void showBatch(List<BatchRules.Entry> entries,boolean agreed,String expanded){
        if(batchDialog!=null)batchDialog.dismiss();batchDraft=BatchRules.copy(entries);batchExpanded=expanded;batchInputs.clear();batchExpansions.clear();batchImpacts.clear();LinearLayout body=Ui.column(this),footer=Ui.column(this);body.setFocusableInTouchMode(true);LinearLayout presets=Ui.row(this);for(int n:new int[]{15,30,60}){Button b=Ui.button(this,"全部 "+n+" 分",false,()->{for(EditText input:new ArrayList<>(batchInputs.values()))input.setText(String.valueOf(n));});b.setTextSize(12);Ui.pad(b,6,8);presets.addView(b,new LinearLayout.LayoutParams(0,-2,1));if(n!=60)Ui.gap(presets,7);}body.addView(presets);Ui.space(body,13);LinearLayout rows=Ui.column(this);body.addView(rows);for(BatchRules.Entry e:batchDraft)rows.addView(batchRow(e));Ui.space(body,10);body.addView(Ui.button(this,"＋ 继续选择应用",false,()->{List<BatchRules.Entry> kept=BatchRules.copy(batchDraft);batchDialog.dismiss();openPicker(kept,"");}));
        batchError=Ui.text(this,"",12,Ui.AMBER);batchError.setVisibility(View.GONE);footer.addView(batchError);batchImpact=Ui.bold(this,"",12);footer.addView(batchImpact);batchRefill=Ui.text(this,"",12,Ui.MUTED);footer.addView(batchRefill);batchDeadline=Ui.text(this,"",12,Ui.MUTED);footer.addView(batchDeadline);batchConsent=consent("以上额度保存后锁定七天，期间不能修改或删除",agreed);batchConsent.setId(11003);footer.addView(batchConsent);batchSave=Ui.button(this,"保存并锁定 "+batchDraft.size()+" 项",true,()->{});batchSave.setId(11002);footer.addView(batchSave,new LinearLayout.LayoutParams(-1,-2));
        Dialog d=sheet("批量设置额度",body,footer);batchDialog=d;final boolean[] saving={false},committed={false};batchConsent.setOnCheckedChangeListener((v,on)->refreshBatchGate());batchSave.setOnClickListener(v->{if(saving[0]||committed[0]||!batchConsent.isChecked())return;List<BatchRules.Entry> payload=BatchRules.copy(batchDraft);if(!validMinutes(payload)){showBatchError("请输入 0–1440 的整数分钟");return;}if(!GuardService.alive()){showBatchError(protectionMessage(protection()));return;}try{validateInstalled(payload);}catch(RuntimeException e){showBatchError(e.getMessage());return;}MotionUi.tap(v);saving[0]=true;batchSave.setEnabled(false);if(mutate(book->BatchRules.applyNew(book,payload,store.now()))){committed[0]=true;d.dismiss();showSuccess();}else{saving[0]=false;refreshBatchGate();showBatchError(store.writeError.isEmpty()?"保存失败，原规则未变":store.writeError);}});
        d.setOnDismissListener(x->{if(batchDialog==d){batchDialog=null;batchDraft=new ArrayList<>();batchInputs.clear();batchExpansions.clear();batchImpacts.clear();batchConsent=null;batchDeadline=batchImpact=batchRefill=null;batchSave=null;batchError=null;batchExpanded="";}});refreshBatchGate();body.requestFocus();
    }
    private View batchRow(BatchRules.Entry e){LinearLayout box=Ui.card(this);LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=dp(10);box.setLayoutParams(outer);LinearLayout identity=Ui.row(this);identity.addView(appIcon(e.pkg),new LinearLayout.LayoutParams(dp(30),dp(30)));Ui.gap(identity,9);TextView title=Ui.bold(this,e.name,14);title.setMaxLines(2);identity.addView(title,new LinearLayout.LayoutParams(0,-2,1));View remove=iconButton("close","移除 "+e.name,()->{if(batchConsent!=null)batchConsent.setChecked(false);batchDraft.remove(e);EditText old=batchInputs.remove(e.pkg);if(old!=null)old.clearFocus();batchExpansions.remove(e.pkg);batchImpacts.remove(e.pkg);if(box.getParent() instanceof ViewGroup)((ViewGroup)box.getParent()).removeView(box);hideKeyboard(box);refreshBatchGate();});identity.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(identity);
        LinearLayout amount=Ui.row(this);amount.addView(Ui.text(this,"分钟 / 天",12,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));EditText input=numberInput(e.minutes<0?"":String.valueOf(e.minutes),23);input.setContentDescription(e.name+" 每日分钟数");amount.addView(input,new LinearLayout.LayoutParams(dp(getResources().getConfiguration().fontScale>1.4f?140:98),-2));FlowUi.TimeBand band=new FlowUi.TimeBand(this,Math.max(0,e.minutes));FlowUi.Expansion expanded=new FlowUi.Expansion(this);expanded.addView(band,new FrameLayout.LayoutParams(-1,dp(116)));amount.addView(iconButton("sliders","展开 "+e.name+" 时间带",()->{String old=batchExpanded;batchExpanded=e.pkg.equals(old)?"":e.pkg;for(Map.Entry<String,FlowUi.Expansion> x:batchExpansions.entrySet())x.getValue().open(x.getKey().equals(batchExpanded),true);}),new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(amount);box.addView(expanded);TextView impact=Ui.text(this,"",12,Ui.INK);Ui.space(box,7);box.addView(impact);batchImpacts.put(e.pkg,impact);batchInputs.put(e.pkg,input);batchExpansions.put(e.pkg,expanded);expanded.open(e.pkg.equals(batchExpanded),false);final boolean[] fromBand={false};band.listener=n->{fromBand[0]=true;input.setText(String.valueOf(n));fromBand[0]=false;};input.addTextChangedListener(watcher(()->{e.minutes=parseMinutes(input.getText().toString(),-1);if(!fromBand[0]&&e.minutes>=0&&e.minutes<=1440)band.setValue(e.minutes);if(batchConsent!=null)batchConsent.setChecked(false);refreshBatchGate();}));return box;}
    private void refreshBatchGate(){if(batchSave==null)return;boolean valid=validMinutes(batchDraft);batchSave.setText("保存并锁定 "+batchDraft.size()+" 项");batchSave.setEnabled(valid&&batchConsent!=null&&batchConsent.isChecked());if(batchError!=null){batchError.setText(valid?"":batchDraft.isEmpty()?"尚未选择应用":"请输入 0–1440 的整数分钟");batchError.setVisibility(valid?View.GONE:View.VISIBLE);}updateDraftPreview(store.now());}
    private boolean validMinutes(List<BatchRules.Entry> entries){if(entries.isEmpty())return false;for(BatchRules.Entry e:entries)if(e.minutes<0||e.minutes>1440)return false;return true;}
    private void showBatchError(String value){if(batchError!=null){batchError.setText(value);batchError.setVisibility(View.VISIBLE);}}
    private void validateInstalled(List<BatchRules.Entry> entries){for(BatchRules.Entry e:entries)try{ApplicationInfo info=getPackageManager().getApplicationInfo(e.pkg,0);if(!info.enabled)throw new IllegalStateException(e.name+" 已停用");}catch(PackageManager.NameNotFoundException ex){throw new IllegalStateException(e.name+" 已不可用，请重新选择");}}
    private void edit(String pkg,String name,int minutes,boolean agreed){showEditor(pkg,name,String.valueOf(minutes),agreed,false);}
    private void showEditor(String pkg,String name,String raw,boolean agreed,boolean requireNew){
        if(!requireNew&&store.book.locked(pkg,store.now())){ruleOptions(pkg);return;}if(editor!=null)editor.dismiss();LinearLayout body=Ui.column(this),footer=Ui.column(this);body.addView(Ui.text(this,name,15,Ui.MUTED));Ui.space(body,12);EditText input=numberInput(raw,42);input.setId(10001);input.setContentDescription("每天可用分钟数");body.addView(input,new LinearLayout.LayoutParams(-1,-2));TextView unit=Ui.text(this,"分钟 / 天",12,Ui.MUTED);unit.setGravity(Gravity.CENTER);body.addView(unit);Ui.space(body,7);FlowUi.TimeBand band=new FlowUi.TimeBand(this,parseMinutes(raw,30));band.setId(10006);body.addView(band,new LinearLayout.LayoutParams(-1,dp(116)));LinearLayout presets=Ui.row(this);for(int n:new int[]{15,30,60}){Button b=Ui.button(this,n+" 分",false,()->input.setText(String.valueOf(n)));b.setTextSize(12);Ui.pad(b,8,8);presets.addView(b,new LinearLayout.LayoutParams(0,-2,1));if(n!=60)Ui.gap(presets,8);}body.addView(presets);Ui.space(body,12);singleImpact=Ui.bold(this,"",13);body.addView(singleImpact);Ui.space(body,4);singleRefill=Ui.text(this,"",12,Ui.MUTED);body.addView(singleRefill);
        TextView error=Ui.text(this,"",12,Ui.AMBER);error.setVisibility(View.GONE);footer.addView(error);singleDeadline=Ui.text(this,"",12,Ui.MUTED);footer.addView(singleDeadline);CheckBox agree=consent("保存后锁定七天，期间不能修改或删除",agreed);agree.setId(10002);footer.addView(agree);Button save=Ui.button(this,"保存并锁定七天",true,()->{});save.setId(10007);footer.addView(save,new LinearLayout.LayoutParams(-1,-2));Dialog d=sheet("设置每日额度",body,footer);editor=d;minuteInput=input;lockConsent=agree;draftPkg=pkg;draftName=name;draftNew=requireNew;
        final boolean[] fromBand={false},saving={false},committed={false};Runnable gate=()->{int n=parseMinutes(input.getText().toString(),-1);boolean valid=n>=0&&n<=1440;save.setEnabled(valid&&agree.isChecked()&&!saving[0]);error.setText(valid?"":"请输入 0–1440 的整数分钟");error.setVisibility(valid?View.GONE:View.VISIBLE);updateDraftPreview(store.now());};band.listener=n->{fromBand[0]=true;input.setText(String.valueOf(n));fromBand[0]=false;};input.addTextChangedListener(watcher(()->{int n=parseMinutes(input.getText().toString(),-1);if(!fromBand[0]&&n>=0&&n<=1440)band.setValue(n);agree.setChecked(false);gate.run();}));agree.setOnCheckedChangeListener((v,on)->gate.run());save.setOnClickListener(v->{if(saving[0]||committed[0]||!agree.isChecked())return;int n=parseMinutes(input.getText().toString(),-1);if(n<0||n>1440){gate.run();return;}if(!GuardService.alive()){error.setText(protectionMessage(protection()));error.setVisibility(View.VISIBLE);return;}List<BatchRules.Entry> payload=Collections.singletonList(new BatchRules.Entry(pkg,name,n));if(requireNew)try{validateInstalled(payload);}catch(RuntimeException ex){error.setText(ex.getMessage());error.setVisibility(View.VISIBLE);return;}MotionUi.tap(v);saving[0]=true;save.setEnabled(false);hideKeyboard(input);boolean ok=mutate(book->{long now=store.now();if(requireNew)BatchRules.applyNew(book,payload,now);else book.put(pkg,name,n,now);});if(ok){committed[0]=true;d.dismiss();showSuccess();}else{saving[0]=false;gate.run();error.setText(store.writeError.isEmpty()?"保存失败，原规则未变":store.writeError);error.setVisibility(View.VISIBLE);}});
        d.setOnDismissListener(x->{if(editor==d){editor=null;minuteInput=null;lockConsent=null;singleDeadline=singleImpact=singleRefill=null;draftPkg=draftName="";draftNew=false;}});gate.run();body.setFocusableInTouchMode(true);body.requestFocus();
    }
    private EditText numberInput(String raw,int size){EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);input.setSingleLine();input.setGravity(Gravity.CENTER);input.setTextSize(size);input.setTextColor(Ui.INK);input.setFontFeatureSettings("tnum");input.setBackground(Ui.shape(Ui.SOFT,dp(16)));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});input.setSelectAllOnFocus(true);input.setMinimumHeight(dp(56));Ui.pad(input,8,8);input.setText(raw);return input;}
    private CheckBox consent(String value,boolean checked){CheckBox c=new CheckBox(this);c.setText(value);c.setTextSize(12);c.setTextColor(Ui.INK);c.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));c.setMinimumHeight(dp(52));c.setChecked(checked);return c;}
    private void quotaImpact(TextView view,String pkg,int minutes){
        if(view==null)return;
        boolean valid=minutes>=0&&minutes<=1440;
        view.setVisibility(valid?View.VISIBLE:View.GONE);
        if(!valid)return;
        long remaining=store.book.remainingWithQuota(pkg,minutes);
        label(view,minutes==0?"保存后全天禁用":remaining==0?"保存后今天立即到限":"保存后今日剩余 "+Ui.duration(remaining));
        view.setTextColor(remaining==0?Ui.AMBER:Ui.INK);
    }
    private void updateDraftPreview(long now){
        if(singleDeadline==null&&batchDeadline==null)return;
        String locked="规则预计可修改："+date(now+RuleBook.WEEK);
        String refills="额度恢复："+date(store.book.nextMidnight(now));
        if(singleDeadline!=null&&minuteInput!=null){
            int minutes=parseMinutes(minuteInput.getText().toString(),-1);
            quotaImpact(singleImpact,draftPkg,minutes);
            singleRefill.setVisibility(minutes>=0&&minutes<=1440?View.VISIBLE:View.GONE);
            label(singleRefill,minutes==0?"全天禁用不自动恢复":refills);label(singleDeadline,locked);
        }
        if(batchDeadline!=null){
            int disabled=0,exhausted=0,limited=0;
            for(BatchRules.Entry entry:batchDraft){
                int minutes=entry.minutes;
                quotaImpact(batchImpacts.get(entry.pkg),entry.pkg,minutes);
                if(minutes==0)disabled++;
                else if(minutes>0&&minutes<=1440){limited++;if(store.book.remainingWithQuota(entry.pkg,minutes)==0)exhausted++;}
            }
            String impact=(exhausted>0?"今天立即到限 "+exhausted+" 项":"")+(exhausted>0&&disabled>0?" · ":"")+(disabled>0?"全天禁用 "+disabled+" 项":"");
            batchImpact.setVisibility(impact.isEmpty()?View.GONE:View.VISIBLE);
            label(batchImpact,impact);batchImpact.setTextColor(Ui.AMBER);
            batchRefill.setVisibility(limited>0?View.VISIBLE:View.GONE);
            label(batchRefill,refills);label(batchDeadline,locked);
        }
    }
    private void hideKeyboard(View v){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    private void showSuccess(){if(stage==null||isDestroyed())return;FlowUi.SuccessMark mark=new FlowUi.SuccessMark(this);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(getResources().getConfiguration().fontScale>1.4f?150:118),dp(48),Gravity.BOTTOM|Gravity.START);lp.leftMargin=dp(20);lp.bottomMargin=dp(86)+root.getPaddingBottom();stage.addView(mark,lp);mark.setClickable(false);mark.setFocusable(false);mark.play();root.announceForAccessibility("规则已保存");handler.postDelayed(()->{if(mark.getParent()==stage){if(MotionUi.enabled(this))mark.animate().alpha(0).setDuration(160).withEndAction(()->stage.removeView(mark)).start();else stage.removeView(mark);}},1050);}
    private TextWatcher watcher(Runnable action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){action.run();}public void afterTextChanged(Editable e){}};}
    private void ruleOptions(String pkg){if(!store.book.rules.containsKey(pkg))return;if(tab!=0)switchTab(0);if(orbitHome!=null){orbitSelectedPkg=pkg;orbitHome.select(pkg);showRuleDetails(pkg);return;}HomeRow row=homeRows.get(pkg);if(row==null)return;String old=expandedPkg;expandedPkg=pkg;if(!old.equals(pkg)&&homeRows.containsKey(old))homeRows.get(old).expansion.open(false,true);row.expansion.open(true,true);row.update(store.now());}

    private String orbitThemeName(){String id=store.prefs.getString("orbit_theme","saturn");return id.equals("star")?"星轨":id.equals("aurora")?"极光":"土星环";}
    private void applyOrbitTheme(String id){
        if(!id.equals("saturn")&&!id.equals("star")&&!id.equals("aurora"))return;
        store.prefs.edit().putString("orbit_theme",id).apply();Ui.theme(this);
        if(root!=null)root.setBackgroundColor(Ui.BG);if(orbitHome!=null)orbitHome.setBackgroundColor(Ui.BG);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);updateValues();
    }
    private void nativeThemePicker(){new AlertDialog.Builder(this).setTitle("外观主题").setItems(new String[]{"土星环","星轨","极光"},(d,i)->{applyOrbitTheme(new String[]{"saturn","star","aurora"}[i]);render();}).setNegativeButton("取消",null).show();}
    private void orbitCommand(String action,String value){
        if(isDestroyed())return;
        switch(action){
            case "ready":updateValues();break;
            case "settings":switchTab(1);break;
            case "protection":openProtectionAction();break;
            case "layout":chooseHomeView(value);break;
            case "add":pickApp();break;
            case "theme":applyOrbitTheme(value);break;
            case "select":if(store.book.rules.containsKey(value))orbitSelectedPkg=value;break;
            case "detail":if(store.book.rules.containsKey(value)){orbitSelectedPkg=value;showRuleDetails(value);}break;
            default:break;
        }
    }
    private void updateOrbit(long now){if(orbitHome!=null)updateOrbit(now,protection());}
    private void updateOrbit(long now,ProtectionRunState.Presentation state){
        if(orbitHome==null)return;
        try{
            org.json.JSONArray apps=new org.json.JSONArray();long sum=0;
            for(String pkg:orderedPackages()){
                RuleBook.Rule r=store.book.rules.get(pkg);long used=store.book.used(pkg),rest=store.book.remaining(pkg);sum+=used;boolean locked=store.book.locked(pkg,now);
                apps.put(new org.json.JSONObject().put("pkg",pkg).put("name",r.name).put("quota",r.minutes).put("remaining",(rest+999)/1000).put("usedMs",used).put("usedLabel","已用 "+Ui.used(used)).put("locked",locked).put("lockLabel",locked?date(r.lockedUntil)+" 可调整":"现在可调整"));
            }
            orbitHome.snapshot(new org.json.JSONObject().put("apps",apps).put("theme",store.prefs.getString("orbit_theme","saturn")).put("selectedPkg",orbitSelectedPkg).put("reduced",!MotionUi.enabled(this))
                .put("protection",state.name()).put("statusLabel",state.label).put("statusMessage",protectionMessage(state)).put("statusAction",state.action).put("corrupt",store.corrupt).put("totalLabel",Ui.used(sum)));
        }catch(org.json.JSONException|RuntimeException error){Diagnostics.event(this,"首页数据刷新失败",error);}
    }
    private void showRuleDetails(String pkg){
        RuleBook.Rule r=store.book.rules.get(pkg);if(r==null)return;if(ruleDialog!=null)ruleDialog.dismiss();
        LinearLayout body=Ui.column(this),identity=Ui.row(this);identity.addView(appIcon(pkg),new LinearLayout.LayoutParams(dp(42),dp(42)));Ui.gap(identity,12);identity.addView(Ui.bold(this,r.name,19),new LinearLayout.LayoutParams(0,-2,1));body.addView(identity);Ui.space(body,18);
        TextView remaining=Ui.bold(this,"",38);remaining.setFontFeatureSettings("tnum");body.addView(remaining);Ui.space(body,16);
        TextView quota=detailLine(body,"每日额度"),used=detailLine(body,"今日已用"),reset=detailLine(body,"额度恢复"),until=detailLine(body,"规则可调整");LinearLayout actions=Ui.column(this);body.addView(actions);boolean[] last={false},initialized={false};
        Dialog dialog=sheet("应用规则",body);ruleDialog=dialog;
        ruleDetailTick=()->{
            RuleBook.Rule current=store.book.rules.get(pkg);if(current==null){dialog.dismiss();return;}long now=store.now(),rest=store.book.remaining(pkg);boolean locked=store.book.locked(pkg,now);
            remaining.setText(current.minutes==0?"全天禁用":rest==0?"已到限":Ui.compact(rest));quota.setText(current.minutes==0?"全天禁用":current.minutes+" 分钟");used.setText(Ui.duration(store.book.used(pkg)));reset.setText(current.minutes==0?"不自动恢复":date(store.book.nextMidnight(now)));until.setText(locked?date(current.lockedUntil):"现在可调整");
            if(!initialized[0]||last[0]!=locked){initialized[0]=true;last[0]=locked;actions.removeAllViews();if(!locked){Ui.space(actions,18);actions.addView(Ui.button(this,"调整每日额度",true,()->{dialog.dismiss();edit(pkg,current.name,current.minutes,false);}),new LinearLayout.LayoutParams(-1,-2));Ui.space(actions,8);actions.addView(Ui.button(this,"删除规则",false,()->{dialog.dismiss();confirmDelete(pkg);}),new LinearLayout.LayoutParams(-1,-2));}}
        };
        dialog.setOnDismissListener(d->{if(ruleDialog==dialog){ruleDialog=null;ruleDetailTick=null;}});ruleDetailTick.run();
    }

    private void warningGuide(){LinearLayout body=Ui.column(this);body.addView(Ui.text(this,"每天每个应用在剩余 5 分钟、1 分钟时各提醒一次。提醒不会延长额度，也不会盖住正在使用的界面。",14,Ui.INK));Ui.space(body,12);body.addView(Ui.text(this,"额度不超过 5 分钟时不发 5 分钟提醒；不超过 1 分钟时不发 1 分钟提醒。系统可能延迟或隐藏通知，额度用完仍会按规则拦截。",12,Ui.MUTED));Ui.space(body,20);final Dialog[] d=new Dialog[1];body.addView(Ui.button(this,"启用额度提醒",true,()->{store.prefs.edit().putBoolean("quota_warning",true).apply();d[0].dismiss();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7011);else QuotaWarnings.update(this,store.book);updateValues();}));Ui.space(body,10);body.addView(Ui.button(this,"系统通知设置",false,()->openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))));Ui.space(body,10);body.addView(Ui.button(this,"关闭额度提醒",false,()->{store.prefs.edit().putBoolean("quota_warning",false).apply();QuotaWarnings.update(this,store.book);d[0].dismiss();updateValues();}));d[0]=sheet("额度提醒",body);}
    private int parseMinutes(String value,int fallback){try{return Integer.parseInt(value);}catch(NumberFormatException e){return fallback;}}

    private boolean mutate(java.util.function.Consumer<RuleBook> action){
        Set<String> before=new HashSet<>(store.book.rules.keySet());try{if(store.corrupt)throw new IllegalStateException("规则数据损坏");store.mutate(action);for(String pkg:store.book.rules.keySet())if(!before.contains(pkg)){orbitSelectedPkg=pkg;break;}}catch(RuntimeException e){toast(e.getMessage());return false;}
        try{PolicyOps.sync(this,GuardService.alive());PolicyOps.schedule(this);GuardService.refresh();}catch(RuntimeException e){Diagnostics.event(this,"规则已保存，保护同步失败",e);}
        scrollPositions[tab]=scroll.getScrollY();render();return true;
    }
    private void help(){
        String content="仅支持 Android 8 及以上。\n\n普通模式需要持续开启无障碍，并允许后台运行。额度统计从创建规则开始；分屏里可见的受限应用分别计时。锁屏暂停计时。应用分身、网页版和其他用户空间不在本版本的限制范围。\n\n同一次开机内，修改时间不会提前解锁；普通模式重启后的离线间隔仍依赖系统时间，无法完全防篡改。\n\n设备管理增强模式需要先用电脑把本应用配置成『设备所有者』，不是普通设备管理员。通常要求无账号、未配置的新机/测试机；不要为了试用直接清空主力手机。增强模式可挂起到时的应用，并在七天锁定期禁止常规卸载、限制清数据和强停（Android 11+），开启自动时间。它仍不保证防止关闭无障碍、Root、刷机或系统恢复。\n\n未在你的手机上实测，请先用一个不重要的应用设 1 分钟验证。\n\n不申请联网权限；不读取聊天或截图。";
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("使用说明").setMessage(content).setNegativeButton("关闭",null);
        if(PolicyOps.owner(this)){
            if(!PolicyOps.enabled(this))b.setPositiveButton("启用增强模式",(d,w)->new AlertDialog.Builder(this).setTitle("启用系统管理策略？").setMessage("锁定期内会禁止本应用及被锁定应用的常规卸载、清数据/强停（Android 11+）和手动改时间；额度用完时挂起对应应用。无障碍停止后，系统检查到服务中断时会暂时挂起所有受限应用。所有七天锁定结束后，可在这里退出设备管理。\n\n开启自动时间后，退出不会替你关闭自动时间。").setPositiveButton("同意并启用",(x,y)->{store.prefs.edit().putBoolean("owner_enabled",true).commit();PolicyOps.sync(this,GuardService.alive());render();}).setNegativeButton("取消",null).show());
            else b.setPositiveButton("退出设备管理",(d,w)->{if(store.book.anyLocked(store.now())){toast("仍有规则锁定中，七天结束后才能退出");return;}new AlertDialog.Builder(this).setTitle("退出设备管理？").setMessage("将解除本应用施加的设备管理策略并恢复普通模式。重新启用设备所有者需要重新配置。此测试版使用 Android 的测试用途退出接口，部分系统可能拒绝；本应用不会执行恢复出厂设置。").setPositiveButton("确认退出",(x,y)->{try{PolicyOps.leave(this);render();}catch(RuntimeException e){toast(e.getMessage());}}).setNegativeButton("取消",null).show();});
        }
        b.show();
    }
    private void toast(String message){Toast.makeText(this,message==null?"操作失败":message,Toast.LENGTH_LONG).show();}
}
