package com.luma.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** 内置背景配色:只有颜色值,不读图、不占内存。 */
final class Backgrounds {

    static final String DEFAULT = "default";

    /** key 顺序即展示顺序 */
    static final String[] KEYS = {"default", "blue", "graphite", "brown", "green", "purple"};

    private static final int[] COLORS = {
            0xFF0B0E13, // 深空黑(默认)
            0xFF0D1520, // 墨蓝
            0xFF14161A, // 石墨灰
            0xFF17120E, // 深棕
            0xFF0C1512, // 墨绿
            0xFF120F1A  // 暗紫
    };

    private Backgrounds() {
    }

    static int color(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return COLORS[i];
            }
        }
        return COLORS[0];
    }

    /** 同色系上下渐变,让纯色背景有一点层次,不引入图片 */
    static GradientDrawable gradient(String key) {
        int base = color(key);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(base, 0x12), base});
    }

    private static int blend(int c, int amount) {
        return Color.rgb(
                Math.min(255, Color.red(c) + amount),
                Math.min(255, Color.green(c) + amount),
                Math.min(255, Color.blue(c) + amount));
    }

    static String name(Context c, String key) {
        switch (key == null ? DEFAULT : key) {
            case "blue": return c.getString(R.string.bg_blue);
            case "graphite": return c.getString(R.string.bg_graphite);
            case "brown": return c.getString(R.string.bg_brown);
            case "green": return c.getString(R.string.bg_green);
            case "purple": return c.getString(R.string.bg_purple);
            case DEFAULT:
            default: return c.getString(R.string.bg_default);
        }
    }

    static CharSequence[] names(Context c) {
        CharSequence[] out = new CharSequence[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            out[i] = name(c, KEYS[i]);
        }
        return out;
    }

    static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }
}
