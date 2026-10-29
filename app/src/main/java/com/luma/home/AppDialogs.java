package com.luma.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 长按应用弹出的操作菜单:固定/取消固定、应用信息、卸载。 */
final class AppDialogs {

    private static final String SETTINGS_PKG = "com.android.settings";
    private static final String DETAILS_ACT = "com.android.settings.applications.InstalledAppDetailsTop";

    private AppDialogs() {
    }

    static void showPinMenu(final Activity a, final AppEntry e) {
        final boolean pinned = AppStore.isPinned(a, e.pkg);

        List<String> labels = new ArrayList<>();
        labels.add(a.getString(pinned ? R.string.app_unpin : R.string.app_pin));
        labels.add(a.getString(R.string.menu_app_info));
        if (!e.system) {
            labels.add(a.getString(R.string.menu_uninstall));
        }

        new AlertDialog.Builder(a)
                .setTitle(e.label)
                .setItems(labels.toArray(new CharSequence[0]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    togglePin(a, e, pinned);
                                } else if (which == 1) {
                                    openAppInfo(a, e.pkg);
                                } else {
                                    openUninstall(a, e.pkg);
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void togglePin(Activity a, AppEntry e, boolean pinned) {
        if (pinned) {
            AppStore.unpin(a, e.pkg);
            Toast.makeText(a, R.string.app_unpinned_toast, Toast.LENGTH_SHORT).show();
            refresh(a);
            return;
        }

        if (AppStore.pinned(a).size() < AppRepo.MAX_PINNED) {
            AppStore.pin(a, e.pkg);
            Toast.makeText(a, R.string.app_pinned_toast, Toast.LENGTH_SHORT).show();
            refresh(a);
            return;
        }

        final String oldestPkg = AppStore.oldestPinned(a);
        AppEntry oldest = oldestPkg == null ? null : AppRepo.find(a, oldestPkg);
        final String oldestLabel = oldest == null ? String.valueOf(oldestPkg) : oldest.label;

        new AlertDialog.Builder(a)
                .setTitle(R.string.app_pin)
                .setMessage(a.getString(R.string.app_pin_full, oldestLabel))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (oldestPkg != null) {
                            AppStore.replace(a, oldestPkg, e.pkg);
                        } else {
                            AppStore.pin(a, e.pkg);
                        }
                        Toast.makeText(a, R.string.app_pinned_toast, Toast.LENGTH_SHORT).show();
                        refresh(a);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 直接打开原生设置的「应用信息」页;这台机器上标准 action 会被应用管家抢走 */
    private static void openAppInfo(Activity a, String pkg) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(SETTINGS_PKG, DETAILS_ACT));
            i.setData(Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void openUninstall(Activity a, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private static void refresh(Activity a) {
        if (a instanceof LauncherActivity) {
            ((LauncherActivity) a).refreshRows();
        }
    }
}
