package com.luma.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 信号源的持久化:只存"用户改过的部分"(名字/图标/是否常用),
 * 没改过的走内置默认值,这样系统以后增删源也不会把配置弄乱。
 */
final class SourceStore {

    private static final String FILE = "launcher_sources";
    private static final String KEY_DATA = "data";

    /** 首页最多显示几个常用信源 */
    static final int MAX_PINNED = 4;

    private SourceStore() {
    }

    // ---------- 默认值 ----------

    static String defaultName(Context c, int id) {
        switch (id) {
            case 0: return c.getString(R.string.src_atv);
            case 1: return c.getString(R.string.src_dvbc);
            case 2: return c.getString(R.string.src_dtmb);
            case 3: return c.getString(R.string.src_av1);
            case 4: return c.getString(R.string.src_av2);
            case 5: return c.getString(R.string.src_av3);
            case 6: return c.getString(R.string.src_vga);
            case 7: return c.getString(R.string.src_ypbpr1);
            case 8: return c.getString(R.string.src_ypbpr2);
            case 9: return c.getString(R.string.src_hdmi1);
            case 10: return c.getString(R.string.src_hdmi2);
            case 11: return c.getString(R.string.src_hdmi3);
            case 12: return c.getString(R.string.src_hdmi4);
            case 13: return c.getString(R.string.src_media);
            case 14: return c.getString(R.string.src_scart1);
            case 15: return c.getString(R.string.src_scart2);
            case 16: return c.getString(R.string.src_auto);
            default: return c.getString(R.string.src_generic, id);
        }
    }

    static String defaultIcon(int id) {
        switch (id) {
            case 0:
            case 1:
            case 2:
            case 16:
                return IconLib.TV;
            case 13:
                return IconLib.MEDIA;
            case 6:
                return IconLib.PC;
            default:
                return IconLib.HDMI;
        }
    }

    // ---------- 读写 ----------

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static Map<Integer, SourceItem> loadOverrides(Context c) {
        Map<Integer, SourceItem> map = new HashMap<>();
        String raw = sp(c).getString(KEY_DATA, "");
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\\|", -1);
            if (p.length < 5) {
                continue;
            }
            try {
                SourceItem it = new SourceItem(Integer.parseInt(p[0]), p[1], p[2]);
                it.pinned = "1".equals(p[3]);
                it.pinnedAt = Long.parseLong(p[4]);
                map.put(it.id, it);
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static void saveAll(Context c, Map<Integer, SourceItem> map) {
        StringBuilder sb = new StringBuilder();
        for (SourceItem it : map.values()) {
            sb.append(it.id).append('|')
                    .append(safe(it.name)).append('|')
                    .append(safe(it.iconKey)).append('|')
                    .append(it.pinned ? '1' : '0').append('|')
                    .append(it.pinnedAt).append('\n');
        }
        sp(c).edit().putString(KEY_DATA, sb.toString()).apply();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('|', ' ').replace('\n', ' ');
    }

    /** 系统当前可用的源(数字 id),拿不到时退化成内置全集 */
    static List<Integer> knownIds(Context c) {
        List<Integer> ids = TvApi.getSourceIds();
        if (ids == null || ids.isEmpty()) {
            ids = new ArrayList<>();
            for (int i = 0; i <= 16; i++) {
                ids.add(i);
            }
        }
        return ids;
    }

    /** 全部源(已合并用户自定义) */
    static List<SourceItem> all(Context c) {
        Map<Integer, SourceItem> ov = loadOverrides(c);
        List<SourceItem> out = new ArrayList<>();
        for (Integer id : knownIds(c)) {
            SourceItem it = ov.get(id);
            if (it == null) {
                it = new SourceItem(id, defaultName(c, id), defaultIcon(id));
            }
            out.add(it);
        }
        Collections.sort(out, new Comparator<SourceItem>() {
            @Override
            public int compare(SourceItem a, SourceItem b) {
                return a.id - b.id;
            }
        });
        return out;
    }

    static SourceItem get(Context c, int id) {
        for (SourceItem it : all(c)) {
            if (it.id == id) {
                return it;
            }
        }
        return new SourceItem(id, defaultName(c, id), defaultIcon(id));
    }

    /** 首次使用:铺 4 个最常用的信源,别让首页只剩一张“添加”卡 */
    /** 把旧版“媒体”信源存的盒子图标换成播放图标,只迁移一次 */
    static void fixMediaIcon(Context c) {
        Map<Integer, SourceItem> ov = loadOverrides(c);
        SourceItem it = ov.get(13);
        if (it != null && IconLib.BOX.equals(it.iconKey)) {
            it.iconKey = IconLib.MEDIA;
            saveAll(c, ov);
        }
    }

    static void seedDefaults(Context c) {
        List<Integer> available = knownIds(c);
        int[] preferred = {9, 10, 11, 13, 0, 1, 2, 6};
        List<Integer> pick = new ArrayList<>();
        for (int id : preferred) {
            if (pick.size() >= MAX_PINNED) {
                break;
            }
            if (available.contains(id) && !pick.contains(id)) {
                pick.add(id);
            }
        }
        for (int id : available) {
            if (pick.size() >= MAX_PINNED) {
                break;
            }
            if (!pick.contains(id)) {
                pick.add(id);
            }
        }
        // pinned() 按 pinnedAt 倒序,倒着固定才能让 9/10/11/13 保持从左到右
        for (int i = pick.size() - 1; i >= 0; i--) {
            setPinned(c, pick.get(i), true);
        }
    }

    /** 首页常用源,按固定时间倒序(最新固定在最前) */
    static List<SourceItem> pinned(Context c) {
        List<SourceItem> out = new ArrayList<>();
        for (SourceItem it : all(c)) {
            if (it.pinned) {
                out.add(it);
            }
        }
        Collections.sort(out, new Comparator<SourceItem>() {
            @Override
            public int compare(SourceItem a, SourceItem b) {
                return Long.compare(b.pinnedAt, a.pinnedAt);
            }
        });
        if (out.size() > MAX_PINNED) {
            out = out.subList(0, MAX_PINNED);
        }
        return out;
    }

    /** 固定位满时,最早固定的那个(等待用户确认是否顶掉) */
    static SourceItem oldestPinned(Context c) {
        List<SourceItem> list = pinned(c);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    static void save(Context c, SourceItem item) {
        Map<Integer, SourceItem> ov = loadOverrides(c);
        SourceItem old = ov.get(item.id);
        if (old != null) {
            item.pinnedAt = item.pinned ? (old.pinned ? old.pinnedAt : item.pinnedAt) : 0;
        }
        ov.put(item.id, item);
        saveAll(c, ov);
    }

    static void setPinned(Context c, int id, boolean pinned) {
        SourceItem it = get(c, id);
        it.pinned = pinned;
        it.pinnedAt = pinned ? System.currentTimeMillis() : 0;
        save(c, it);
    }

    static void unpin(Context c, int id) {
        setPinned(c, id, false);
    }
}
