package com.luma.home;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 拾光桌面主页:顶栏 + 常用信源 + 应用行。
 *
 * 信源卡片:OK 直接切源,长按进入编辑。
 * 应用行:4 个固定位,宽卡只显示图标,名字由上方焦点大字显示。
 */
public class LauncherActivity extends Activity {

    private static final int APP_SLOTS = 4;
    /** 应用卡尺寸与间距(参照 tvOS:竖间距≈卡高30%,横间距≈卡宽11%) */
    private static final int CARD_W_DP = 262;
    private static final int CARD_H_DP = 150;
    private static final int CARD_GAP_DP = 28;
    /** 氛围光的整体强度。35% 实测晃眼,tvOS 实际观感在 15~20% 左右 */
    private static final int AMBIENT_ALPHA = 0x33;
    /** 未聚焦卡片压暗到多少,1.0 = 不压 */
    static final float CARD_DIM = 0.9f;
    /** 焦点动画时长:太快会有"跳"的感觉 */
    static final int FOCUS_ANIM_MS = 180;

    private TextView tvClock;
    private TextView tvDate;
    private TextView tvFocusTitle;
    private TextView tvStats;
    private LinearLayout rowSources;
    private LinearLayout rowApps;
    private LinearLayout gridApps;
    private View ambient;
    private ValueAnimator ambientAnim;
    private int ambientColor;
    private String lastSignature;
    private boolean gridFocused;
    private final Map<String, android.graphics.Bitmap> artCache = new HashMap<>();
    private final Map<String, Integer> glowCache = new HashMap<>();

    /**
     * 装/删/更新应用后必须作废 AppRepo 的静态缓存,
     * 否则桌面进程活着的时候"全部应用"永远停在打开第一眼看到的那个列表。
     */
    private final BroadcastReceiver pkgWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppRepo.invalidate();
            artCache.clear();
            glowCache.clear();
            lastSignature = null;   // 下次 onResume 强制重建
        }
    };

    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    // 日期格式跟着系统语言走,中英文都能显示
    private final SimpleDateFormat dateFmt = new SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(
                    Locale.getDefault(), "MMMEd"), Locale.getDefault());

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 20000);
        }
    };

    /** 系统时间/时区一变就刷新,不用等下一次 tick */
    private final BroadcastReceiver timeWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    /**
     * 开机初期系统时间还没从 NTP 同步过来(1970 年),
     * 这时不能照实显示 00:00 / 1月1日,先显示 --:-- ,同步后自动变正常。
     */
    private void updateClock() {
        Date now = new Date();
        if (now.getTime() < 1262304000000L) {   // 2010-01-01
            tvClock.setText("--:--");
            tvDate.setText("");
        } else {
            tvClock.setText(timeFmt.format(now));
            tvDate.setText(dateFmt.format(now));
        }
        updateStats();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        tvClock = findViewById(R.id.tv_clock);
        tvDate = findViewById(R.id.tv_date);
        tvFocusTitle = findViewById(R.id.tv_focus_title);
        tvStats = findViewById(R.id.tv_stats);
        rowSources = findViewById(R.id.row_sources);
        rowApps = findViewById(R.id.row_apps);
        gridApps = findViewById(R.id.grid_apps);
        // 上下留白只对网格生效:托盘本来就在首屏,给留白会导致一聚焦就滚一下
        ((TvScrollView) findViewById(R.id.scroll)).setMarginPolicy(
                new TvScrollView.MarginPolicy() {
                    @Override
                    public boolean extraMargins(View focused) {
                        return isInside(focused, gridApps);
                    }
                });
        ambient = findViewById(R.id.ambient);
        ambient.setBackground(new ColorDrawable(0x00000000));

        findViewById(R.id.root).getViewTreeObserver().addOnGlobalFocusChangeListener(
                new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override
                    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                        if (newFocus == null || !isInside(newFocus, rowApps)) {
                            clearFocusTitle();
                        }
                        // 进入网格时把托盘顶到屏幕最上方,给网格腾出整屏;
                        // 框内继续移动不再重复滚动,交给系统自动滚。
                        boolean inGrid = newFocus != null && isInside(newFocus, gridApps);
                        boolean inDock = newFocus != null && isInside(newFocus, rowApps);
                        if (inGrid) {
                            gridFocused = true;
                            clearFocusTitle();
                            ensureMargins(newFocus);
                        } else {
                            gridFocused = false;
                            // 回到托盘时恢复首屏构图
                            if (inDock) {
                                scrollToTop();
                            }
                        }
                    }
                });

        findViewById(R.id.btn_source).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LauncherActivity.this, SourcePanelActivity.class));
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LauncherActivity.this, SettingsActivity.class));
            }
        });

        findViewById(R.id.btn_media).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 优先用设置里选的媒体应用;没有/已卸载则回落夏普播放器
                Intent i = null;
                String pkg = Prefs.mediaApp(LauncherActivity.this);
                if (pkg != null && pkg.length() > 0) {
                    i = getPackageManager().getLaunchIntentForPackage(pkg);
                }
                if (i == null) {
                    i = getPackageManager().getLaunchIntentForPackage("com.sharp.localmm");
                }
                if (i == null) {
                    i = new Intent();
                    i.setComponent(new ComponentName("com.sharp.localmm",
                            "com.sharp.localmm.activity.TabBarExample"));
                }
                try {
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(LauncherActivity.this,
                            R.string.media_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 顶栏图标不要框;焦点时变实心、变蓝并轻微放大
        // 顶栏最左/最右再按方向键就停在原地,不要跳到下面的内容区
        findViewById(R.id.btn_source).setNextFocusLeftId(R.id.btn_source);
        findViewById(R.id.btn_settings).setNextFocusRightId(R.id.btn_settings);
        bindNavIcon(R.id.btn_source);
        bindNavIcon(R.id.btn_media);
        bindNavIcon(R.id.btn_settings);

        // 监听装/删应用,作废应用列表缓存
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(pkgWatcher, pkgFilter);

        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(timeWatcher, timeFilter);

        // 首次使用:自动填最近安装的应用
        if (!Prefs.firstRunDone(this)) {
            AppRepo.ensureFirstRun(this);
            Prefs.setFirstRunDone(this);
        }
        // 恢复出厂 / 首次安装:先铺 4 个常用信源,避免首页只剩一张“添加”卡
        if (!Prefs.sourcesSeeded(this)) {
            if (SourceStore.pinned(this).isEmpty()) {
                SourceStore.seedDefaults(this);
            }
            Prefs.setSourcesSeeded(this);
        }
        if (!Prefs.mediaIconMigrated(this)) {
            SourceStore.fixMediaIcon(this);
            Prefs.setMediaIconMigrated(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvFocusTitle.setVisibility(Prefs.showFocusTitle(this) ? View.VISIBLE : View.GONE);
        tvStats.setVisibility(Prefs.showStats(this) ? View.VISIBLE : View.GONE);
        findViewById(R.id.root).setBackground(
                Backgrounds.gradient(Prefs.background(this)));
        // 从工厂菜单回来时视频窗口可能还停在测试画面尺寸,恢复成整屏
        TvApi.resetWindowRect();
        clockTick.run();

        // 内容没变就不重建视图,避免每次回桌面都重新膨胀一遍布局
        String sig = signature();
        if (!sig.equals(lastSignature)) {
            lastSignature = sig;
            buildSourceRow();
            buildAppRow();
            buildGrid();
        }
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (SourceItem s : SourceStore.pinned(this)) {
            sb.append(s.id).append(s.name).append(s.iconKey).append(s.pinnedAt).append(';');
        }
        sb.append('|').append(TvApi.getCurrent(this));
        sb.append('|');
        for (String p : AppStore.pinned(this)) {
            sb.append(p).append(':').append(AppUsage.lastUsed(this, p)).append(',');
        }
        sb.append('|').append(AppRepo.visible(this).size());
        return sb.toString();
    }

    /** 底部状态行:剩余内存 / 存储 */
    private void updateStats() {
        if (!Prefs.showStats(this)) {
            tvStats.setText("");
            return;
        }
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long ramMb = mi.availMem / (1024L * 1024L);

            android.os.StatFs fs = new android.os.StatFs(getFilesDir().getAbsolutePath());
            long freeBytes = (long) fs.getAvailableBlocks() * fs.getBlockSize();
            String storage = String.format(Locale.getDefault(), "%.1f GB",
                    freeBytes / (1024.0 * 1024.0 * 1024.0));

            tvStats.setText(getString(R.string.stats_fmt, ramMb, storage));
        } catch (Throwable t) {
            tvStats.setText("");
        }
    }

    /**
     * 顶栏图标:选中态只用「更亮的胶囊段 + 轻微放大」,不改颜色。
     * 之前把图标染成主题蓝,在深色背景上太跳;单色靠明度表达更克制。
     */
    private void bindNavIcon(int id) {
        findViewById(id).setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s).setDuration(FOCUS_ANIM_MS).start();
                setAmbient(0);   // 顶栏不参与氛围光
            }
        });
    }

    /**
     * 氛围光:背景整屏染上一层焦点内容的颜色,tvOS 首页的做法。
     * 全部用 alpha 混合,颜色本身保持很暗,不干扰前景文字对比度。
     *
     * 熄灯带 1 秒延迟:焦点从应用行移到信源/顶栏时,先让光停留一会儿,
     * 避免每步导航都"亮-灭-亮"地闪;期间回到应用行会直接换色,不经过黑。
     */
    private static final long AMBIENT_OFF_DELAY_MS = 1000L;

    private final Runnable ambientOff = new Runnable() {
        @Override
        public void run() {
            fadeAmbientTo(0x00000000);
        }
    };

    private void setAmbient(int rgb) {
        // rgb 为 0 表示请求熄灯
        int target = rgb == 0 ? 0x00000000 : (AMBIENT_ALPHA << 24 | (rgb & 0xFFFFFF));
        if (target == ambientColor) {
            return;
        }
        handler.removeCallbacks(ambientOff);
        if (target == 0x00000000) {
            handler.postDelayed(ambientOff, AMBIENT_OFF_DELAY_MS);
            return;
        }
        fadeAmbientTo(target);
    }

    private void fadeAmbientTo(int target) {
        int from = ambientColor;
        ambientColor = target;
        if (ambientAnim != null) {
            ambientAnim.cancel();
        }
        ambientAnim = ValueAnimator.ofObject(new ArgbEvaluator(), from, target);
        ambientAnim.setDuration(450);
        ambientAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ColorDrawable d = (ColorDrawable) ambient.getBackground();
                if (d != null) {
                    d.setColor((Integer) animation.getAnimatedValue());
                }
            }
        });
        ambientAnim.start();
    }

    private void showFocusTitle(String name) {
        if (Prefs.showFocusTitle(this)) {
            tvFocusTitle.setText(name);
            tvFocusTitle.setVisibility(View.VISIBLE);
        }
    }

    /** 焦点离开应用行:恢复栏目名,不能残留上一个应用名 */
    /** 没有选中托盘里的应用时,这一行整条不显示 */
    private void clearFocusTitle() {
        tvFocusTitle.setVisibility(View.INVISIBLE);
    }

    /**
     * 托盘里的横向移动限制在托盘内。
     * 托盘右侧的空位不是可聚焦控件,系统会继续往右找到网格里的第二个应用,
     * 看起来就像"跳过第一个"。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus != null && isInside(focus, rowApps)) {
                int key = event.getKeyCode();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View next = focus.focusSearch(key == KeyEvent.KEYCODE_DPAD_LEFT
                            ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                    if (next == null || !isInside(next, rowApps)) {
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 桌面上按返回不该退出。
     * 默认实现会 finish() 掉这个 HOME Activity,系统随即重建一个,
     * 表现就是"闪一下、重新加载桌面"。
     */
    @Override
    public void onBackPressed() {
        // 吞掉:桌面是最底层的界面,没有"返回上一级"
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(ambientOff);
        try {
            unregisterReceiver(pkgWatcher);
        } catch (Throwable ignored) {
        }
        try {
            unregisterReceiver(timeWatcher);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 按「下」打开全部应用。
     *
     * 在 Activity 层拦截,避免 LinearLayout 的默认焦点搜索把焦点移到旁边的卡片,
     * 也避免在 app 行上按一下只是跳焦点。
     */
    private boolean isInside(View view, View parent) {
        while (view != null) {
            if (view == parent) {
                return true;
            }
            ViewParent p = view.getParent();
            view = p instanceof View ? (View) p : null;
        }
        return false;
    }

    /** 应用固定状态变化后由对话框回调,重建两行 */
    void refreshRows() {
        buildSourceRow();
        buildAppRow();
        buildGrid();
    }

    /**
     * 把一行的左右焦点连成闭环:第一个再按左、最后一个再按右都停在原地。
     * 否则系统会按几何位置继续搜索,跑到别的区块去(比如信源末尾按右跳到网格)。
     */
    private void chainHorizontal(LinearLayout row) {
        List<View> items = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v.isFocusable()) {
                if (v.getId() == View.NO_ID) {
                    v.setId(View.generateViewId());
                }
                items.add(v);
            }
        }
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            View left = i > 0 ? items.get(i - 1) : v;
            View right = i < items.size() - 1 ? items.get(i + 1) : v;
            v.setNextFocusLeftId(left.getId());
            v.setNextFocusRightId(right.getId());
        }
    }

    // ---------------- 信源行 ----------------

    private void buildSourceRow() {
        rowSources.removeAllViews();
        List<SourceItem> pinned = SourceStore.pinned(this);

        // 一个都没固定时收缩成一张引导卡,不留空洞
        if (pinned.isEmpty()) {
            View add = makeAddCard(
                    R.string.add_source, dp(284), dp(150),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(new Intent(LauncherActivity.this,
                                    SourcePanelActivity.class));
                        }
                    });
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) add.getLayoutParams();
            lp.rightMargin = 0;
            add.setLayoutParams(lp);
            rowSources.addView(add);
            return;
        }

        int current = TvApi.getCurrent(this);
        for (int i = 0; i < pinned.size(); i++) {
            SourceItem it = pinned.get(i);
            View card = makeSourceCard(it, it.id == current);
            if (i == pinned.size() - 1) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
                lp.rightMargin = 0;
                card.setLayoutParams(lp);
            }
            rowSources.addView(card);
        }
        chainHorizontal(rowSources);
    }

    private View makeSourceCard(final SourceItem item, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(14), dp(12), dp(12));
        card.setAlpha(CARD_DIM);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(284), dp(150));
        lp.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(IconLib.res(item.iconKey));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(80), dp(80));
        icon.setLayoutParams(ip);
        card.addView(icon);

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextColor(getResources().getColor(
                active ? R.color.accent : R.color.text_primary));
        name.setTextSize(20f);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(250),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(10);
        name.setLayoutParams(np);
        card.addView(name);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("LumaSrc", "click id=" + item.id + " name=" + item.name);
                TvApi.switchTo(LauncherActivity.this, item.id);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent i = new Intent(LauncherActivity.this, SourceEditActivity.class);
                i.putExtra(SourceEditActivity.EXTRA_SOURCE_ID, item.id);
                startActivity(i);
                return true;
            }
        });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s)
                        .alpha(hasFocus ? 1f : CARD_DIM).setDuration(FOCUS_ANIM_MS).start();
                setAmbient(0);   // 信源行不参与氛围光
            }
        });
        return card;
    }

    // ---------------- 应用行 ----------------

    private void buildAppRow() {
        rowApps.removeAllViews();

        List<AppEntry> row = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // 顺手清掉已卸载应用的固定记录,否则会占着名额却显示不出来
        for (String pkg : new ArrayList<>(AppStore.pinned(this))) {
            if (AppRepo.find(this, pkg) == null) {
                AppStore.unpin(this, pkg);
            }
        }

        // 1. 用户固定的,按固定时间倒序
        for (String pkg : AppStore.pinned(this)) {
            if (row.size() >= APP_SLOTS) {
                break;
            }
            AppEntry e = AppRepo.find(this, pkg);
            if (e != null && used.add(pkg)) {
                row.add(e);
            }
        }
        Collections.sort(row, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                long ta = AppUsage.lastUsed(LauncherActivity.this, a.pkg);
                long tb = AppUsage.lastUsed(LauncherActivity.this, b.pkg);
                if (ta != tb) {
                    return Long.compare(tb, ta);
                }
                long pa = AppStore.pinnedAt(LauncherActivity.this, a.pkg);
                long pb = AppStore.pinnedAt(LauncherActivity.this, b.pkg);
                if (pa != pb) {
                    return Long.compare(pb, pa);
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });

        for (AppEntry e : row) {
            rowApps.addView(makeAppSlot(e));
        }
        // 只显示固定应用;没有固定应用时给一张引导卡,不再自动把未固定应用补进来
        if (row.isEmpty()) {
            rowApps.addView(makeAppsEmptyCard());
        }
        // 胶囊固定 4 格宽:不足的用隐形占位补齐,宽度不随应用数量变化
        while (rowApps.getChildCount() < APP_SLOTS) {
            View spacer = new View(this);
            LinearLayout.LayoutParams slp =
                    new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP));
            slp.rightMargin = dp(CARD_GAP_DP);
            spacer.setLayoutParams(slp);
            spacer.setFocusable(false);
            rowApps.addView(spacer);
        }
        // 托盘自带内边距,最后一张卡不要再留右边距,否则右侧会多出一截
        int last = rowApps.getChildCount() - 1;
        if (last >= 0) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) rowApps.getChildAt(last).getLayoutParams();
            lp.rightMargin = 0;
            rowApps.getChildAt(last).setLayoutParams(lp);
        }
        chainHorizontal(rowApps);
        // 默认把焦点放在第一项上,顺便让顶部大字有内容
        if (rowApps.getChildCount() > 0) {
            rowApps.getChildAt(0).requestFocus();
        }
        // 焦点落位会把页面滚一下,首屏构图就不可控了;
        // 布局结束后回到顶部,保证"露出一条边"是设计出来的,不是碰运气
        final View sc = findViewById(R.id.scroll);
        if (sc != null) {
            sc.post(new Runnable() {
                @Override
                public void run() {
                    sc.scrollTo(0, 0);
                }
            });
        }
    }

    // ---------------- 应用网格(首页下半部分) ----------------

    /**
     * 全部应用直接长在首页下面,和托盘同属一条滚动竖线。
     * 每行 6 个,聚焦时 ScrollView 会自动把它滚进视野。
     */
    private void buildGrid() {
        gridApps.removeAllViews();
        // 已经固定在托盘里的应用不再重复出现在下面
        Set<String> pinned = new HashSet<>(AppStore.pinned(this));
        List<AppEntry> apps = new ArrayList<>();
        for (AppEntry e : AppRepo.visible(this)) {
            if (!pinned.contains(e.pkg)) {
                apps.add(e);
            }
        }
        gridApps.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);

        // 按最后使用时间倒序(和之前"全部应用"页的规则一致),没用过的按名字排
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                long ta = AppUsage.lastUsed(LauncherActivity.this, a.pkg);
                long tb = AppUsage.lastUsed(LauncherActivity.this, b.pkg);
                if (ta != tb) {
                    return Long.compare(tb, ta);
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });

        final int cols = 4;
        for (int i = 0; i < apps.size(); i += cols) {
            // 每行一个 block:卡片行 + 下面的名字标签(只在该行有焦点时显示)
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setClipChildren(false);
            block.setClipToPadding(false);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(14);   // 和托盘内边距对齐
            if (i > 0) {
                blp.topMargin = dp(14);
            }
            block.setLayoutParams(blp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            block.addView(row);

            // 行内名字:占一行固定高度,只在该行有焦点时显示
            TextView name = new TextView(this);
            name.setTextSize(20f);
            name.setTextColor(getResources().getColor(R.color.text_secondary));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setVisibility(View.INVISIBLE);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            nlp.topMargin = dp(20);
            name.setLayoutParams(nlp);
            block.addView(name);
            block.setTag(name);

            View[] rowSlots = new View[cols];
            for (int j = 0; j < cols; j++) {
                int idx = i + j;
                if (idx < apps.size()) {
                    AppEntry e = apps.get(idx);
                    View slot = makeAppSlot(e);
                    slot.setId(View.generateViewId());
                    rowSlots[j] = slot;
                    if (j == cols - 1) {
                        LinearLayout.LayoutParams lp =
                                (LinearLayout.LayoutParams) slot.getLayoutParams();
                        lp.rightMargin = 0;
                        slot.setLayoutParams(lp);
                    }
                    row.addView(slot);
                } else {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP));
                    if (j != cols - 1) {
                        lp.rightMargin = dp(CARD_GAP_DP);
                    }
                    spacer.setLayoutParams(lp);
                    row.addView(spacer);
                }
            }
            // 行内左右只在同一行里走:首列再按左、末列再按右都停在原地
            for (int j = 0; j < cols; j++) {
                View slot = rowSlots[j];
                if (slot == null) {
                    continue;
                }
                View left = j > 0 ? rowSlots[j - 1] : null;
                View right = null;
                for (int k = j + 1; k < cols; k++) {
                    if (rowSlots[k] != null) {
                        right = rowSlots[k];
                        break;
                    }
                }
                slot.setNextFocusLeftId(left != null ? left.getId() : slot.getId());
                slot.setNextFocusRightId(right != null ? right.getId() : slot.getId());
            }
            gridApps.addView(block);
        }
    }

    /** 没有固定应用时的引导卡:按「下」去全部应用里固定 */
    private View makeAppsEmptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setAlpha(CARD_DIM);
        card.setLayoutParams(new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP)));

        TextView glyph = new TextView(this);
        glyph.setText("≡");
        glyph.setTextColor(getResources().getColor(R.color.accent));
        glyph.setTextSize(38f);
        glyph.setGravity(Gravity.CENTER);
        card.addView(glyph);

        TextView label = new TextView(this);
        label.setText(R.string.hint_down_all_apps);
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTextSize(18f);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        label.setLayoutParams(lp);
        card.addView(label);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrollToGrid();
            }
        });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s)
                        .alpha(hasFocus ? 1f : CARD_DIM).setDuration(FOCUS_ANIM_MS).start();
                setAmbient(0);
            }
        });
        return card;
    }

    /** 滚到应用网格(首页的下半部分) */
    private void scrollToGrid() {
        View sc = findViewById(R.id.scroll);
        if (sc instanceof ScrollView) {
            ((ScrollView) sc).smoothScrollTo(0, gridApps.getTop());
        }
    }

    /**
     * 焦点在网格里移动后,按"视觉边界 + 呼吸空间"校正一次滚动。
     *
     * 用两次 post:第一次等这一帧的布局/滚动落定,第二次再读真实坐标,
     * 然后是同步 scrollBy(不做动画),所以用户看到的是一次干净的位置,
     * 不会有"先贴边、再滑走"的二次动作。
     */
    private void ensureMargins(final View focused) {
        final View sc = findViewById(R.id.scroll);
        if (!(sc instanceof ScrollView)) {
            return;
        }
        focused.postDelayed(new Runnable() {
            @Override
            public void run() {
                applyMargins(sc, focused);
            }
        }, 40);
    }

    private void applyMargins(View sc, View focused) {
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        int expandY = Math.round(focused.getHeight() * 0.03f);
        int top = loc[1] - expandY;
        int bottom = loc[1] + focused.getHeight() + expandY;

        int scrollTop = sc.getScrollY();
        int viewTop = sc.getTop();
        int viewBottom = viewTop + sc.getHeight();

        int delta = 0;
        if (top - dp(36) < viewTop) {
            delta = (top - dp(36)) - viewTop;
        } else if (bottom + dp(60) > viewBottom) {
            delta = (bottom + dp(60)) - viewBottom;
        }
        if (delta != 0) {
            ((ScrollView) sc).smoothScrollBy(0, delta);
        }
    }

    /** 回到首屏:页面滚到最顶部(等焦点滚动落定后再滚,否则会被覆盖) */
    private void scrollToTop() {
        final View sc = findViewById(R.id.scroll);
        if (sc instanceof ScrollView) {
            sc.post(new Runnable() {
                @Override
                public void run() {
                    ((ScrollView) sc).smoothScrollTo(0, 0);
                }
            });
        }
    }

    /** 「＋ 添加」引导卡 */
    private View makeAddCard(int labelRes, int widthPx, int heightPx,
                             View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(8), dp(12), dp(8), dp(12));
        card.setAlpha(CARD_DIM);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, heightPx);
        lp.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(lp);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(getResources().getColor(R.color.accent));
        plus.setTextSize(40f);
        plus.setGravity(Gravity.CENTER);
        card.addView(plus);

        TextView name = new TextView(this);
        name.setText(labelRes);
        name.setTextColor(getResources().getColor(R.color.text_primary));
        name.setTextSize(18f);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(8);
        name.setLayoutParams(np);
        card.addView(name);

        card.setOnClickListener(listener);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s)
                        .alpha(hasFocus ? 1f : CARD_DIM).setDuration(FOCUS_ANIM_MS).start();
                setAmbient(0);
            }
        });
        return card;
    }

    private View makeAppSlot(final AppEntry e) {
        LinearLayout slot = baseSlot();

        android.graphics.Bitmap art = artCache.get(e.pkg);
        if (art == null) {
            art = AppArt.tile(e, dp(CARD_W_DP - 4), dp(CARD_H_DP - 4), dp(10));
            if (art != null) {
                artCache.put(e.pkg, art);
            }
        }

        FrameLayout wrap = new FrameLayout(this);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView artView = new ImageView(this);
        if (art != null) {
            artView.setScaleType(ImageView.ScaleType.FIT_XY);
            artView.setImageBitmap(art);
        } else {
            artView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            artView.setPadding(dp(28), dp(20), dp(28), dp(20));
            artView.setImageDrawable(e.icon);
        }
        artView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrap.addView(artView);

        slot.addView(wrap);

        slot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    AppUsage.touch(LauncherActivity.this, e.pkg);
                    startActivity(e.intent);
                } catch (Exception ignored) {
                }
            }
        });
        slot.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AppDialogs.showPinMenu(LauncherActivity.this, e);
                return true;
            }
        });
        slot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s)
                        .alpha(hasFocus ? 1f : CARD_DIM).setDuration(FOCUS_ANIM_MS).start();
                setAmbient(hasFocus ? appGlow(e) : 0);
                if (hasFocus) {
                    // 标题属于「应用」这一栏(托盘),网格里的焦点不该改它
                    if (isInside(v, rowApps)) {
                        showFocusTitle(e.label);
                    }
                    showRowName(v, e.label);
                } else {
                    hideRowName(v);
                }
            }
        });
        return slot;
    }

    /**
     * 网格的行内名字:只在聚焦时出现在两行之间的空隙里,
     * 水平对齐到当前卡片。托盘没有 tag,自然跳过。
     */
    private void showRowName(View card, String label) {
        Object tag = rowLabelOf(card);
        if (!(tag instanceof TextView)) {
            return;
        }
        TextView name = (TextView) tag;
        name.setText(label);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) name.getLayoutParams();
        lp.width = card.getWidth();
        lp.leftMargin = card.getLeft();
        lp.rightMargin = 0;
        name.setLayoutParams(lp);
        name.setAlpha(0f);
        name.setVisibility(View.VISIBLE);
        name.animate().alpha(1f).setDuration(120).start();
    }

    private void hideRowName(View card) {
        Object tag = rowLabelOf(card);
        if (tag instanceof TextView) {
            ((TextView) tag).setVisibility(View.INVISIBLE);
        }
    }

    private Object rowLabelOf(View card) {
        View row = (View) card.getParent();
        View block = row == null ? null : (View) row.getParent();
        return block == null ? null : block.getTag();
    }

    /** 应用图标的氛围色,算过一次就缓存,焦点移动时不再重复取色 */
    private int appGlow(AppEntry e) {
        Integer c = glowCache.get(e.pkg);
        if (c == null) {
            c = AppArt.colorOf(e.icon);
            glowCache.put(e.pkg, c);
        }
        return c;
    }

    private LinearLayout baseSlot() {
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER);
        slot.setFocusable(true);
        slot.setFocusableInTouchMode(true);
        slot.setBackgroundResource(R.drawable.bg_card);
        slot.setPadding(dp(2), dp(2), dp(2), dp(2));
        slot.setAlpha(CARD_DIM);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP));
        lp.setMargins(0, 0, dp(CARD_GAP_DP), 0);
        slot.setLayoutParams(lp);

        slot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1.0f;
                v.animate().scaleX(s).scaleY(s).setDuration(FOCUS_ANIM_MS).start();
            }
        });
        return slot;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
