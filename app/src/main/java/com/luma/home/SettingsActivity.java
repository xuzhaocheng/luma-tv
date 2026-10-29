package com.luma.home;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/** 设置:开机默认信源、首页应用/信源入口、关于。 */
public class SettingsActivity extends Activity {

    private TextView tvRemoteValue;
    private TextView tvSourcesValue;
    private TextView tvShowInternalValue;
    private TextView tvDefaultHomeValue;
    private TextView tvMediaValue;
    private TextView tvTitleValue;
    private TextView tvStatsValue;
    private TextView tvBgValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        View rowFactory = findViewById(R.id.row_factory);
        ((TextView) rowFactory.findViewById(R.id.tv_label)).setText(R.string.settings_factory);
        rowFactory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFactoryMenu();
            }
        });

        View rowRemote = findViewById(R.id.row_remote_key);
        ((TextView) rowRemote.findViewById(R.id.tv_label)).setText(R.string.settings_remote_key);
        tvRemoteValue = rowRemote.findViewById(R.id.tv_value);
        rowRemote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, RemoteKeyActivity.class));
            }
        });

        View rowSources = findViewById(R.id.row_home_sources);
        ((TextView) rowSources.findViewById(R.id.tv_label)).setText(R.string.settings_home_sources);
        tvSourcesValue = rowSources.findViewById(R.id.tv_value);
        rowSources.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, SourcePanelActivity.class));
            }
        });

        View rowDefaultHome = findViewById(R.id.row_default_home);
        ((TextView) rowDefaultHome.findViewById(R.id.tv_label)).setText(R.string.settings_default_home);
        tvDefaultHomeValue = rowDefaultHome.findViewById(R.id.tv_value);
        rowDefaultHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDefaultHomeDialog();
            }
        });

        View rowMedia = findViewById(R.id.row_media_app);
        ((TextView) rowMedia.findViewById(R.id.tv_label)).setText(R.string.settings_media_app);
        tvMediaValue = rowMedia.findViewById(R.id.tv_value);
        rowMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickMediaApp();
            }
        });

        View rowShowInternal = findViewById(R.id.row_show_internal_apps);
        ((TextView) rowShowInternal.findViewById(R.id.tv_label)).setText(R.string.settings_show_internal_apps);
        tvShowInternalValue = rowShowInternal.findViewById(R.id.tv_value);
        rowShowInternal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setShowInternalApps(SettingsActivity.this,
                        !Prefs.showInternalApps(SettingsActivity.this));
                refreshValues();
            }
        });

        View rowBg = findViewById(R.id.row_background);
        ((TextView) rowBg.findViewById(R.id.tv_label)).setText(R.string.settings_background);
        tvBgValue = rowBg.findViewById(R.id.tv_value);
        rowBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickBackground();
            }
        });

        View rowTitle = findViewById(R.id.row_focus_title);
        ((TextView) rowTitle.findViewById(R.id.tv_label)).setText(R.string.settings_focus_title);
        tvTitleValue = rowTitle.findViewById(R.id.tv_value);
        rowTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setShowFocusTitle(SettingsActivity.this, !Prefs.showFocusTitle(SettingsActivity.this));
                refreshValues();
            }
        });

        View rowStats = findViewById(R.id.row_stats);
        ((TextView) rowStats.findViewById(R.id.tv_label)).setText(R.string.settings_stats);
        tvStatsValue = rowStats.findViewById(R.id.tv_value);
        rowStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setShowStats(SettingsActivity.this, !Prefs.showStats(SettingsActivity.this));
                refreshValues();
            }
        });

        ((TextView) findViewById(R.id.tv_about)).setText(
                getString(R.string.settings_version)
                        + "\n" + Build.MANUFACTURER + " " + Build.MODEL
                        + "  ·  Android " + Build.VERSION.RELEASE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshValues();
    }

    private void refreshValues() {

        int remoteKey = Prefs.remoteKey(this);
        if (remoteKey < 0) {
            tvRemoteValue.setText(R.string.remote_learn_none);
        } else {
            String key = RemoteKeyActivity.keyName(this, remoteKey);
            if (Prefs.remoteLong(this)) {
                key = getString(R.string.remote_key_long_fmt, key);
            }
            tvRemoteValue.setText(getString(R.string.remote_summary, key,
                    SourceStore.get(this, Prefs.remoteSource(this)).name));
        }

        tvSourcesValue.setText(String.valueOf(SourceStore.pinned(this).size()));
        tvShowInternalValue.setText(Prefs.showInternalApps(this) ? R.string.on : R.string.off);
        tvDefaultHomeValue.setText(currentHomeLabel());
        tvMediaValue.setText(mediaAppLabel());
        tvTitleValue.setText(Prefs.showFocusTitle(this) ? R.string.on : R.string.off);
        tvStatsValue.setText(Prefs.showStats(this) ? R.string.on : R.string.off);
        tvBgValue.setText(Backgrounds.name(this, Prefs.background(this)));
    }

    private String currentHomeLabel() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (pkg != null && !"android".equals(pkg) && !pkg.contains("Resolver")) {
                    CharSequence label = ri.loadLabel(getPackageManager());
                    return label == null || label.length() == 0 ? pkg : label.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return getString(R.string.default_home_unknown);
    }

    private void showDefaultHomeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.default_home_title)
                .setMessage(getString(R.string.default_home_msg, currentHomeLabel()))
                .setPositiveButton(R.string.default_home_clear_luma,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                clearOwnDefault();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 直接清掉自己的默认桌面设置,不用跳系统设置页。
     * 正常模式下 com.android.settings 是被夏普替换掉的(关工厂模式后不存在),
     * 而通用 action 会被应用管家劫持,所以能自己干就自己干。
     * Android 4.4 允许应用清除「自己」的 preferred activities。
     */
    private void clearOwnDefault() {
        try {
            getPackageManager().clearPackagePreferredActivities(getPackageName());
            Toast.makeText(this, R.string.default_home_cleared, Toast.LENGTH_LONG).show();
            refreshValues();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.default_home_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String mediaAppLabel() {
        String pkg = Prefs.mediaApp(this);
        if (pkg != null && pkg.length() > 0) {
            try {
                CharSequence l = getPackageManager()
                        .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
                if (l != null && l.length() > 0) {
                    return l.toString();
                }
            } catch (Throwable ignored) {
                // 选中的应用已卸载,展示成默认;启动时也有同样的回落
            }
        }
        return getString(R.string.media_app_default);
    }

    private void pickMediaApp() {
        final List<AppEntry> apps = AppRepo.visible(this);
        final List<String> pkgs = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();

        labels.add(getString(R.string.media_app_default));
        pkgs.add("");

        for (AppEntry e : apps) {
            labels.add(e.label);
            pkgs.add(e.pkg);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_media_app)
                .setItems(labels.toArray(new CharSequence[0]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Prefs.setMediaApp(SettingsActivity.this, pkgs.get(which));
                                refreshValues();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickBackground() {
        final String[] keys = Backgrounds.KEYS;
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_background)
                .setSingleChoiceItems(Backgrounds.names(this),
                        Backgrounds.indexOf(Prefs.background(this)),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Prefs.setBackground(SettingsActivity.this, keys[which]);
                                refreshValues();
                                d.dismiss();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 打开原厂工厂菜单(FactoryAdjust)。开机默认信源这类全局设置都在里面,
     * ADB 开关也在里面,所以我们不再自己实现。
     * 原厂桌面被禁用/替换时,这也是唯一能进工厂菜单的入口。
     */
    private void openFactoryMenu() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.android.FactoryAdjust",
                    "com.android.FactoryAdjust.FactoryActivity"));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.factory_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}