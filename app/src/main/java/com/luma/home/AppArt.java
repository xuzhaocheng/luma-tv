package com.luma.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * 把方形应用图标合成成 16:9 卡面。
 *
 * Android 4.4 没有标准的 banner API,直接拿 icon 铺满宽卡会留一大片空。
 * 这里按 CleanYourTV 的 fallback 思路:裁掉透明边,画到同色系渐变底上,
 * 图标保持完整,卡面看起来像一张 banner。
 */
final class AppArt {

    private AppArt() {
    }

    static Bitmap tile(AppEntry e, int w, int h, int radius) {
        if (e == null || e.icon == null || w <= 0 || h <= 0) {
            return null;
        }
        try {
            Bitmap square = draw(e.icon, 144, 144);
            Bitmap icon = trim(square);
            int tint = tint(icon);

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mix(0xFF202833, tint, 0.28f), mix(0xFF0D1117, tint, 0.16f)});
            bg.setCornerRadius(radius);
            bg.setBounds(0, 0, w, h);
            bg.draw(c);

            int iw = icon.getWidth(), ih = icon.getHeight();
            float target = Math.min(w * 0.40f, h * 0.68f);
            float scale = Math.min(target / iw, target / ih);
            int dw = Math.max(1, Math.round(iw * scale));
            int dh = Math.max(1, Math.round(ih * scale));
            int left = (w - dw) / 2;
            int top = (h - dh) / 2;

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(icon, null, new Rect(left, top, left + dw, top + dh), p);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 图标主色,给首页氛围光用:焦点落在哪张卡,背景就微微染上哪个色。
     * 48×48 足够算平均色,开销可以忽略,不需要缓存。
     */
    static int colorOf(Drawable d) {
        if (d == null) {
            return 0xFF4C9BFF;
        }
        try {
            Bitmap bmp = draw(d, 48, 48);
            int c = tint(bmp);
            bmp.recycle();
            return c;
        } catch (Throwable t) {
            return 0xFF4C9BFF;
        }
    }

    private static Bitmap draw(Drawable d, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Rect old = new Rect(d.getBounds());
        d.setBounds(0, 0, w, h);
        d.draw(c);
        d.setBounds(old);
        return bmp;
    }

    /** 裁掉图标四周的透明边,让图形在卡面里显得更大 */
    private static Bitmap trim(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if (Color.alpha(row[x]) > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return src;
        }
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        if (cw == w && ch == h) {
            return src;
        }
        return Bitmap.createBitmap(src, minX, minY, cw, ch);
    }

    /** 图标里出现最多的颜色,用来给渐变底染色 */
    private static int tint(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        long r = 0, g = 0, b = 0, n = 0;
        int step = Math.max(1, Math.min(w, h) / 32);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int p = src.getPixel(x, y);
                int a = Color.alpha(p);
                if (a < 24) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        if (n == 0) {
            return 0xFF4C9BFF;
        }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private static int mix(int a, int b, float t) {
        float u = 1f - t;
        return Color.rgb(
                Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t),
                Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }
}
