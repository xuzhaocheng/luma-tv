package com.luma.home;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * 遥控器快捷键:拦截用户学习的那一个按键,直接切到指定信源。
 *
 * 两种模式:
 *  - 短按(专用):这个键被完全接管,原功能不再触发;
 *  - 长按(兼容):按下先放行,700ms 后触发并吞掉后续事件。
 *    短按是否还能保留原功能,取决于系统在 DOWN 还是 UP 处理该键,需真机确认。
 *
 * 设置页可见时全部放行,避免配置时被快捷键抢走。
 */
public class RemoteKeyService extends AccessibilityService {

    private static final String TAG = "RemoteKey";
    private static final long LONG_PRESS_MS = 700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean fired;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (!fired) {
                fired = true;
                trigger();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setServiceInfo failed", t);
        }
        Log.d(TAG, "service connected");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // 配置页可见时放行,否则用户没法操作/学习
        if (RemoteKeyState.activityVisible) {
            return false;
        }
        if (!Prefs.remoteEnabled(this)) {
            return false;
        }
        int code = Prefs.remoteKey(this);
        if (code < 0 || event.getKeyCode() != code) {
            return false;
        }

        boolean longMode = Prefs.remoteLong(this);
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                fired = false;
                if (longMode) {
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                }
            }
            // 专用模式:按下就吞,避免系统同时响应
            // 兼容模式:首次按下先放行;长按触发后吞掉重复事件
            return longMode ? fired : true;
        }

        if (action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            if (!longMode && !fired) {
                fired = true;
                trigger();
            }
            boolean consume = !longMode || fired;
            fired = false;
            return consume;
        }

        // 长按后的重复事件:已触发就吞掉
        return longMode && fired;
    }

    private void trigger() {
        int id = Prefs.remoteSource(this);
        boolean ok = TvApi.switchTo(this, id);
        String name = SourceStore.get(this, id).name;
        Toast.makeText(this, ok ? getString(R.string.remote_switched_fmt, name)
                : getString(R.string.remote_switch_sent), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "trigger source=" + id + " ok=" + ok);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(longPress);
        fired = false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(longPress);
        super.onDestroy();
    }
}
