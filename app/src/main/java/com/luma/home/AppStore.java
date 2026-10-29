package com.luma.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 首页固定应用的持久化。 */
final class AppStore {

    private static final String FILE = "launcher_apps";
    private static final String KEY_PINNED = "pinned";

    private AppStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 返回 [包名, 固定时间] 的原始表 */
    private static List<String[]> raw(Context c) {
        List<String[]> out = new ArrayList<>();
        String s = sp(c).getString(KEY_PINNED, "");
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String line : s.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\\|", -1);
            if (p.length >= 2) {
                out.add(p);
            }
        }
        return out;
    }

    private static void save(Context c, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) {
            sb.append(r[0]).append('|').append(r[1]).append('\n');
        }
        sp(c).edit().putString(KEY_PINNED, sb.toString()).apply();
    }

    static List<String> pinned(Context c) {
        List<String[]> rows = raw(c);
        Collections.sort(rows, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Long.compare(parseLong(b[1]), parseLong(a[1]));
            }
        });
        List<String> out = new ArrayList<>();
        for (String[] r : rows) {
            out.add(r[0]);
        }
        return out;
    }

    static boolean isPinned(Context c, String pkg) {
        return pinned(c).contains(pkg);
    }

    static long pinnedAt(Context c, String pkg) {
        for (String[] r : raw(c)) {
            if (r[0].equals(pkg)) {
                return parseLong(r[1]);
            }
        }
        return 0;
    }

    /** 满的时候最早固定的那个;空则返回 null */
    static String oldestPinned(Context c) {
        List<String> list = pinned(c);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    static void pin(Context c, String pkg) {
        List<String[]> rows = raw(c);
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i)[0].equals(pkg)) {
                rows.remove(i);
            }
        }
        rows.add(new String[]{pkg, String.valueOf(System.currentTimeMillis())});
        // 超出上限时淘汰最早的
        while (rows.size() > AppRepo.MAX_PINNED) {
            int oldest = 0;
            for (int i = 1; i < rows.size(); i++) {
                if (parseLong(rows.get(i)[1]) < parseLong(rows.get(oldest)[1])) {
                    oldest = i;
                }
            }
            rows.remove(oldest);
        }
        save(c, rows);
    }

    static void unpin(Context c, String pkg) {
        List<String[]> rows = raw(c);
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i)[0].equals(pkg)) {
                rows.remove(i);
            }
        }
        save(c, rows);
    }

    /** 用新应用顶掉指定应用 */
    static void replace(Context c, String evict, String pkg) {
        unpin(c, evict);
        pin(c, pkg);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
