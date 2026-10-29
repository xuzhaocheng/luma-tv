package com.luma.home;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * 电视用的 ScrollView:焦点滚动统一由这里算。
 *
 * 系统原生的逻辑只保证"矩形进入视口",所以聚焦卡会贴着边;
 * 而焦点卡还会 scale 1.06,视觉上比布局边界更大,贴边更明显。
 *
 * 这里重写 computeScrollDeltaToGetChildRectOnScreen —— ScrollView 的
 * requestChildFocus / requestChildRectangleOnScreen 最终都走它,
 * 是唯一的决策点,改这里就不会出现"多处滚动互相覆盖"的问题:
 *   1. 目标区域按 scale 放大,匹配视觉边界;
 *   2. 上下各留出呼吸空间(只对网格生效,托盘不需要);
 *   3. 只返回一次位移,由系统自己滚,不会再有二次微调。
 */
public class TvScrollView extends ScrollView {

    /** 聚焦卡的放大幅度,与卡片动画保持一致 */
    private static final float CARD_SCALE = 1.06f;

    /** 由外部决定某个焦点元素是否需要上下留白(只有网格需要) */
    public interface MarginPolicy {
        boolean extraMargins(View focused);
    }

    private MarginPolicy marginPolicy;

    /** 焦点元素下方预留的高度(露出下一排的一部分) */
    private final int extraBottom;
    /** 焦点元素上方预留的高度(别贴着屏幕顶边) */
    private final int extraTop;

    public TvScrollView(Context context) {
        this(context, null);
    }

    public TvScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = context.getResources().getDisplayMetrics().density;
        extraBottom = (int) (60 * d + 0.5f);
        extraTop = (int) (36 * d + 0.5f);
    }

    public void setMarginPolicy(MarginPolicy p) {
        marginPolicy = p;
    }

    /**
     * 方向键只用来移动焦点,不做"箭头滚动"。
     * 否则焦点已经在最后一排时,再按下键 ScrollView 会自己再滚一段,
     * 表现为"底部突然又多了空白"。
     */
    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        // ScrollView 的箭��滚动入口;返回 false 表示"这个键我不处理",
        // 方向键只用于移动焦点,页面不会自己滚。
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (focused != null) {
            boolean margins = marginPolicy == null || marginPolicy.extraMargins(focused);
            Rect rect = new Rect();
            focused.getDrawingRect(rect);
            offsetDescendantRectToMyCoords(focused, rect);
            int expandY = Math.round(focused.getHeight() * (CARD_SCALE - 1f) / 2f);
            rect.top -= expandY;
            rect.bottom += expandY;
            if (margins) {
                rect.top -= extraTop;
                rect.bottom += extraBottom;
            }
            int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
            if (delta != 0) {
                scrollBy(0, delta);
            }
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (rect == null || getHeight() == 0) {
            return super.computeScrollDeltaToGetChildRectOnScreen(rect);
        }

        Rect r = new Rect(rect);

        final int screenTop = getScrollY();
        final int screenBottom = screenTop + getHeight();

        if (r.top < screenTop) {
            return r.top - screenTop;          // 往上滚
        }
        if (r.bottom > screenBottom) {
            return r.bottom - screenBottom;    // 往下滚
        }
        return 0;
    }
}
