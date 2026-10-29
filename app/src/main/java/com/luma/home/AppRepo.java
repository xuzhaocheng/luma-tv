package com.luma.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 一个可启动的应用。 */
class AppEntry {

    final String pkg;
    final String label;
    final Drawable icon;
    final Intent intent;
    final boolean system;
    final long firstInstall;

    AppEntry(String pkg, String label, Drawable icon, Intent intent, boolean system, long firstInstall) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.intent = intent;
        this.system = system;
        this.firstInstall = firstInstall;
    }
}

/** 应用列表 + 首页固定的应用。 */
final class AppRepo {

    /**
     * 原厂 launcher 也是用包名列表过滤这些内部服务,不是靠 manifest flag。
     * 这些都带 SYSTEM flag,但和儿童锁等正常系统应用无法用 flag 区分。
     */
    private static final Set<String> INTERNAL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.sharp.android.btbinder",
            "com.sharp.btbinder",
            "com.sharp.hotelmenu",
            "com.sharp.otaupgrade",
            "com.sharp.dtv",
            "com.sharp.fxc.upgrade",
            "com.sharp.inspection",
            "com.sharp.recents",
            "com.sharp.fxc.adservice",
            "com.sharp.fxc.newsetupwiz",
            "com.sharp.fxc.account",
            "com.sharp.fxc.mor.tv",
            "com.sharp.fxc.recordcollect",
            "com.sharp.fxc.fxctopactivity",
            "com.sharp.rs232c"
    ));

    private static List<AppEntry> cache;

    private AppRepo() {
    }

    /**
     * 装/删/更新应用后调用。桌面是常驻进程,不主动作废的话
     * 静态 cache 会让"全部应用"一直显示旧列表。
     */
    static void invalidate() {
        cache = null;
    }

    /** 所有带启动入口的应用,按名字排序 */
    static List<AppEntry> launchable(Context c) {
        if (cache != null) {
            return cache;
        }
        PackageManager pm = c.getPackageManager();
        List<AppEntry> out = new ArrayList<>();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (c.getPackageName().equals(pkg)) {
                continue;
            }
            AppEntry e = new AppEntry(
                    pkg,
                    String.valueOf(ri.loadLabel(pm)),
                    smallIcon(ri.loadIcon(pm)),
                    buildIntent(ri),
                    (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                    installTime(pm, pkg));
            out.add(e);
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        cache = out;
        return out;
    }

    private static long installTime(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0).firstInstallTime;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 应用的图标常常是 512×512 的大图,25 个就可能是几十 MB。
     * 栅格化到 144px(首页放大图标后仍然清晰),内存约 2MB。
     */
    private static Drawable smallIcon(Drawable src) {
        if (src == null) {
            return null;
        }
        final int size = 144;
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            src.setBounds(0, 0, size, size);
            src.draw(canvas);
            return new android.graphics.drawable.BitmapDrawable(null, bmp);
        } catch (Throwable t) {
            return src;
        }
    }

    private static Intent buildIntent(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    static AppEntry find(Context c, String pkg) {
        for (AppEntry e : launchable(c)) {
            if (e.pkg.equals(pkg)) {
                return e;
            }
        }
        return null;
    }

    /** 按设置过滤后的应用列表:默认只隐藏内部服务,系统应用照常显示 */
    static List<AppEntry> visible(Context c) {
        boolean showInternal = Prefs.showInternalApps(c);
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : launchable(c)) {
            if (!showInternal && INTERNAL_PACKAGES.contains(e.pkg)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** 首页固定的包名,按固定时间倒序 */
    static List<String> pinnedPkgs(Context c) {
        return AppStore.pinned(c);
    }

    /** 首页最多 4 个应用位 */
    static final int MAX_PINNED = 4;

    /** 首次使用:自动填最近安装的第三方应用 */
    static void ensureFirstRun(Context c) {
        if (!AppStore.pinned(c).isEmpty()) {
            return;
        }
        List<AppEntry> all = new ArrayList<>();
        for (AppEntry e : launchable(c)) {
            if (!e.system) {
                all.add(e);
            }
        }
        Collections.sort(all, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return Long.compare(b.firstInstall, a.firstInstall);
            }
        });
        for (int i = 0; i < Math.min(MAX_PINNED, all.size()); i++) {
            AppStore.pin(c, all.get(i).pkg);
        }
    }
}
