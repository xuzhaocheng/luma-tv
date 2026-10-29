package com.luma.home;

import android.content.Context;
import android.content.SharedPreferences;

/** 桌面自身的配置。 */
final class Prefs {

    private static final String FILE = "launcher";
    private static final String KEY_FIRST_RUN_DONE = "first_run_done";
    private static final String KEY_SOURCES_SEEDED = "sources_seeded";
    private static final String KEY_MEDIA_ICON_MIGRATED = "media_icon_migrated";
    private static final String KEY_SHOW_FOCUS_TITLE = "show_focus_title";
    private static final String KEY_SHOW_STATS = "show_stats";
    private static final String KEY_SHOW_INTERNAL_APPS = "show_internal_apps";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_MEDIA_APP = "media_app";
    private static final String KEY_REMOTE_ENABLED = "remote_enabled";
    private static final String KEY_REMOTE_KEY = "remote_key";
    private static final String KEY_REMOTE_LONG = "remote_long";
    private static final String KEY_REMOTE_SOURCE = "remote_source";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean firstRunDone(Context c) {
        return sp(c).getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    static void setFirstRunDone(Context c) {
        sp(c).edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply();
    }

    /** 是否已经给首页铺过默认常用信源 */
    static boolean sourcesSeeded(Context c) {
        return sp(c).getBoolean(KEY_SOURCES_SEEDED, false);
    }

    static void setSourcesSeeded(Context c) {
        sp(c).edit().putBoolean(KEY_SOURCES_SEEDED, true).apply();
    }

    static boolean mediaIconMigrated(Context c) {
        return sp(c).getBoolean(KEY_MEDIA_ICON_MIGRATED, false);
    }

    static void setMediaIconMigrated(Context c) {
        sp(c).edit().putBoolean(KEY_MEDIA_ICON_MIGRATED, true).apply();
    }

    /** 顶部大字显示当前焦点应用名 */
    static boolean showFocusTitle(Context c) {
        return sp(c).getBoolean(KEY_SHOW_FOCUS_TITLE, true);
    }

    static void setShowFocusTitle(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_SHOW_FOCUS_TITLE, on).apply();
    }

    /** 底部状态行:剩余内存 / 存储。默认关闭,首屏保持干净 */
    static boolean showStats(Context c) {
        return sp(c).getBoolean(KEY_SHOW_STATS, false);
    }

    static void setShowStats(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_SHOW_STATS, on).apply();
    }

    /** 全部应用里是否显示 BTBinder/HotelMenu 等内部服务,默认关闭 */
    static boolean showInternalApps(Context c) {
        return sp(c).getBoolean(KEY_SHOW_INTERNAL_APPS, false);
    }

    static void setShowInternalApps(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_SHOW_INTERNAL_APPS, on).apply();
    }

    /** 背景配色 key,见 Backgrounds */
    static String background(Context c) {
        return sp(c).getString(KEY_BACKGROUND, Backgrounds.DEFAULT);
    }

    static void setBackground(Context c, String key) {
        sp(c).edit().putString(KEY_BACKGROUND, key).apply();
    }

    /** 顶栏媒体按钮打开的应用包名;空串 = 默认(夏普播放器) */
    static String mediaApp(Context c) {
        return sp(c).getString(KEY_MEDIA_APP, "");
    }

    static void setMediaApp(Context c, String pkg) {
        sp(c).edit().putString(KEY_MEDIA_APP, pkg).apply();
    }

    // ---------- 遥控器快捷键 ----------

    /** 快捷键是否启用 */
    static boolean remoteEnabled(Context c) {
        return sp(c).getBoolean(KEY_REMOTE_ENABLED, false);
    }

    static void setRemoteEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_REMOTE_ENABLED, on).apply();
    }

    /** 学习到的键码,-1 表示还没设置 */
    static int remoteKey(Context c) {
        return sp(c).getInt(KEY_REMOTE_KEY, -1);
    }

    static void setRemoteKey(Context c, int code) {
        sp(c).edit().putInt(KEY_REMOTE_KEY, code).apply();
    }

    /** true=长按触发,false=短按触发 */
    static boolean remoteLong(Context c) {
        return sp(c).getBoolean(KEY_REMOTE_LONG, false);
    }

    static void setRemoteLong(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_REMOTE_LONG, on).apply();
    }

    /** 快捷键切到哪个信源,默认 HDMI1(9) */
    static int remoteSource(Context c) {
        return sp(c).getInt(KEY_REMOTE_SOURCE, 9);
    }

    static void setRemoteSource(Context c, int id) {
        sp(c).edit().putInt(KEY_REMOTE_SOURCE, id).apply();
    }

    static void clearRemote(Context c) {
        sp(c).edit()
                .remove(KEY_REMOTE_ENABLED)
                .remove(KEY_REMOTE_KEY)
                .remove(KEY_REMOTE_LONG)
                .remove(KEY_REMOTE_SOURCE)
                .apply();
    }
}
