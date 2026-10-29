package com.luma.home;

import android.content.Context;
import android.content.SharedPreferences;

/** 记录应用最后启动时间,供“全部应用”排序。 */
final class AppUsage {

    private static final String FILE = "launcher_usage";

    private AppUsage() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static void touch(Context c, String pkg) {
        if (pkg == null) {
            return;
        }
        sp(c).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    static long lastUsed(Context c, String pkg) {
        return sp(c).getLong(pkg, 0L);
    }
}
