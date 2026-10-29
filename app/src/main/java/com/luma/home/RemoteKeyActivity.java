package com.luma.home;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 遥控器快捷键配置页。
 *
 * 学习流程不猜键码:用户按一下想用的键,这里记录 keyCode,再选择目标信源和触发方式。
 * 冲突处理:主页/返回/方向/OK/电源/音量直接拒绝;菜单/信号源/数字键等给出警告。
 */
public class RemoteKeyActivity extends Activity {

    private static final long LEARN_DELAY_MS = 250L;

    private TextView tvService;
    private TextView tvTarget;
    private TextView tvLearn;
    private TextView tvMode;
    private TextView tvEnabled;
    private TextView tvModeDesc;
    private TextView tvHint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean learning;

    private final Runnable startLearn = new Runnable() {
        @Override
        public void run() {
            learning = true;
            tvHint.setText(R.string.remote_learning);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_key);

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        View rowService = findViewById(R.id.row_service);
        ((TextView) rowService.findViewById(R.id.tv_label)).setText(R.string.remote_service);
        tvService = rowService.findViewById(R.id.tv_value);
        rowService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });

        View rowTarget = findViewById(R.id.row_target);
        ((TextView) rowTarget.findViewById(R.id.tv_label)).setText(R.string.remote_target);
        tvTarget = rowTarget.findViewById(R.id.tv_value);
        rowTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTarget();
            }
        });

        View rowLearn = findViewById(R.id.row_learn);
        ((TextView) rowLearn.findViewById(R.id.tv_label)).setText(R.string.remote_learn);
        tvLearn = rowLearn.findViewById(R.id.tv_value);
        rowLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginLearn();
            }
        });

        View rowMode = findViewById(R.id.row_mode);
        ((TextView) rowMode.findViewById(R.id.tv_label)).setText(R.string.remote_mode);
        tvMode = rowMode.findViewById(R.id.tv_value);
        rowMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setRemoteLong(RemoteKeyActivity.this,
                        !Prefs.remoteLong(RemoteKeyActivity.this));
                refresh();
            }
        });

        View rowEnabled = findViewById(R.id.row_enabled);
        ((TextView) rowEnabled.findViewById(R.id.tv_label)).setText(R.string.remote_enabled);
        tvEnabled = rowEnabled.findViewById(R.id.tv_value);
        rowEnabled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEnabled();
            }
        });

        tvModeDesc = findViewById(R.id.tv_mode_desc);
        tvHint = findViewById(R.id.tv_hint);

        findViewById(R.id.btn_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testSwitch();
            }
        });

        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearShortcut();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        RemoteKeyState.activityVisible = true;
        refresh();
    }

    @Override
    protected void onPause() {
        stopLearn();
        RemoteKeyState.activityVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopLearn();
        RemoteKeyState.activityVisible = false;
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!learning) {
            return super.dispatchKeyEvent(event);
        }

        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                stopLearn();
                refresh();
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            learning = false;
            onKeyCaptured(code);
            return true;
        }

        return true;
    }

    private void beginLearn() {
        if (Prefs.remoteSource(this) < 0) {
            Toast.makeText(this, R.string.remote_need_target, Toast.LENGTH_SHORT).show();
            return;
        }
        learning = false;
        tvHint.setText(R.string.remote_learning);
        handler.removeCallbacks(startLearn);
        handler.postDelayed(startLearn, LEARN_DELAY_MS);
    }

    private void stopLearn() {
        learning = false;
        handler.removeCallbacks(startLearn);
        if (tvHint != null) {
            tvHint.setText(R.string.remote_learn_hint);
        }
    }

    private void onKeyCaptured(int code) {
        if (isRejectedKey(code)) {
            Toast.makeText(this, R.string.remote_key_rejected, Toast.LENGTH_LONG).show();
            refresh();
            return;
        }

        final String key = keyName(this, code);
        String target = SourceStore.get(this, Prefs.remoteSource(this)).name;
        StringBuilder msg = new StringBuilder(getString(R.string.remote_confirm_msg, key, target));
        msg.append("\n").append(getString(R.string.remote_key_code_fmt, code));
        if (isRiskyKey(code)) {
            msg.append("\n\n").append(getString(R.string.remote_key_risky));
        }
        msg.append("\n\n").append(getString(R.string.remote_test_hint));

        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_confirm_title)
                .setMessage(msg.toString())
                .setPositiveButton(R.string.remote_confirm_save,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Prefs.setRemoteKey(RemoteKeyActivity.this, code);
                                Prefs.setRemoteEnabled(RemoteKeyActivity.this, true);
                                refresh();
                                Toast.makeText(RemoteKeyActivity.this,
                                        getString(R.string.remote_saved_fmt, key,
                                                SourceStore.get(RemoteKeyActivity.this,
                                                        Prefs.remoteSource(RemoteKeyActivity.this)).name),
                                        Toast.LENGTH_SHORT).show();
                                if (!isServiceEnabled()) {
                                    promptEnableService();
                                }
                            }
                        })
                .setNeutralButton(R.string.remote_confirm_retry,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                beginLearn();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refresh() {
        tvService.setText(isServiceEnabled()
                ? R.string.remote_service_on : R.string.remote_service_off);

        int id = Prefs.remoteSource(this);
        tvTarget.setText(id < 0 ? getString(R.string.remote_learn_none)
                : SourceStore.get(this, id).name);

        int key = Prefs.remoteKey(this);
        if (key < 0) {
            tvLearn.setText(R.string.remote_learn_none);
        } else {
            String name = keyName(this, key);
            tvLearn.setText(Prefs.remoteLong(this)
                    ? getString(R.string.remote_key_long_fmt, name) : name);
        }

        boolean longMode = Prefs.remoteLong(this);
        tvMode.setText(longMode ? R.string.remote_mode_long : R.string.remote_mode_short);
        tvModeDesc.setText(longMode ? R.string.remote_mode_long_desc : R.string.remote_mode_short_desc);
        tvEnabled.setText(Prefs.remoteEnabled(this) ? R.string.on : R.string.off);

        if (!learning) {
            tvHint.setText(R.string.remote_learn_hint);
        }
    }

    private void toggleEnabled() {
        if (Prefs.remoteKey(this) < 0) {
            Toast.makeText(this, R.string.remote_need_key, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean on = !Prefs.remoteEnabled(this);
        Prefs.setRemoteEnabled(this, on);
        refresh();
        if (on && !isServiceEnabled()) {
            promptEnableService();
        }
    }

    private void testSwitch() {
        int id = Prefs.remoteSource(this);
        if (id < 0) {
            Toast.makeText(this, R.string.remote_need_target, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = TvApi.switchTo(this, id);
        Toast.makeText(this, ok ? getString(R.string.remote_switched_fmt,
                SourceStore.get(this, id).name) : getString(R.string.remote_switch_sent),
                Toast.LENGTH_SHORT).show();
    }

    private void pickTarget() {
        final List<SourceItem> all = SourceStore.all(this);
        final List<Integer> ids = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();
        for (SourceItem it : all) {
            ids.add(it.id);
            labels.add(it.name);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_target_title)
                .setItems(labels.toArray(new CharSequence[0]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Prefs.setRemoteSource(RemoteKeyActivity.this, ids.get(which));
                                refresh();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearShortcut() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_clear_title)
                .setMessage(R.string.remote_clear_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.clearRemote(RemoteKeyActivity.this);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptEnableService() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_service_title)
                .setMessage(R.string.remote_service_msg)
                .setPositiveButton(R.string.remote_go_enable,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                openAccessibilitySettings();
                            }
                        })
                .setNegativeButton(R.string.remote_later, null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
            }
            Toast.makeText(this, R.string.remote_need_service, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isServiceEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager)
                    getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) {
                return false;
            }
            for (AccessibilityServiceInfo info : list) {
                ResolveInfo ri = info.getResolveInfo();
                if (ri == null || ri.serviceInfo == null) {
                    continue;
                }
                if (getPackageName().equals(ri.serviceInfo.packageName)
                        && ri.serviceInfo.name != null
                        && ri.serviceInfo.name.endsWith("RemoteKeyService")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 这些键会破坏导航或被电视 MCU 截走,直接拒绝 */
    private static boolean isRejectedKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return true;
            default:
                return false;
        }
    }

    /** 这些键可能和系统功能冲突,允许保存但提示 */
    private static boolean isRiskyKey(int code) {
        if (code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_SETTINGS
                || code == KeyEvent.KEYCODE_TV_INPUT) {
            return true;
        }
        return code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9;
    }

    static String keyName(Context c, int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MENU: return c.getString(R.string.remote_key_menu);
            case KeyEvent.KEYCODE_INFO: return c.getString(R.string.remote_key_info);
            case KeyEvent.KEYCODE_GUIDE: return c.getString(R.string.remote_key_guide);
            case KeyEvent.KEYCODE_TV_INPUT: return c.getString(R.string.remote_key_input);
            case KeyEvent.KEYCODE_SETTINGS: return c.getString(R.string.remote_key_settings);
            case KeyEvent.KEYCODE_PROG_RED: return c.getString(R.string.remote_key_red);
            case KeyEvent.KEYCODE_PROG_GREEN: return c.getString(R.string.remote_key_green);
            case KeyEvent.KEYCODE_PROG_YELLOW: return c.getString(R.string.remote_key_yellow);
            case KeyEvent.KEYCODE_PROG_BLUE: return c.getString(R.string.remote_key_blue);
            default:
                String s = KeyEvent.keyCodeToString(code);
                if (s != null && s.startsWith("KEYCODE_")) {
                    s = s.substring(8);
                }
                if (s == null || s.length() == 0 || "UNKNOWN".equals(s)) {
                    return c.getString(R.string.remote_key_unknown_fmt, code);
                }
                return s;
        }
    }
}
