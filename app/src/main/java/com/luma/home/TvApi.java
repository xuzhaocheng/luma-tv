package com.luma.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 夏普/海思 TV API 的反射封装。
 *
 * 关键事实(已在 LCD-xxBEL7A-C 上实测):
 *  - com.sharp.android.tvapi.jar 在 BOOTCLASSPATH 上,普通 App 可以直接反射调用;
 *  - selectSource(id, true) 返回 true,无 SecurityException;
 *  - 但只切源画面不会变,必须再启动 com.sharp.dtv/.DTVActivity 接管显示。
 */
final class TvApi {

    private static final String TAG = "TvApi";
    private static final String CLS_SHARP_SOURCE = "com.sharp.android.tvapi.source.SharpSource";
    private static final String DTv_PKG = "com.sharp.dtv";
    private static final String DTv_ACT = "com.sharp.dtv.DTVActivity";

    /**
     * 原厂官方切换入口(反编译 com.sharp.atv 得到):
     * ATVReceiver 收到 Action_Start_ATV 后,在自己的进程里按
     * deselect(旧源) -> select(launcher_sourcein) -> startActivity(ATVMainActivity)
     * 的顺序一口气完成,没有跨进程竞争,因此不需要延时补刀。
     */
    private static final String PROP_SOURCE_IN = "launcher_sourcein";
    private static final String ACTION_START_ATV = "Action_Start_ATV";
    /** 官方路径万一在某个 ROM 版本上表现异常,把它置 false 即回退到老路径 */
    private static final boolean PREFER_OFFICIAL_PATH = true;

    private static Object sInstance;
    private static Method sGetCur;
    private static Method sGetList;
    private static Method sSelect;
    private static Method sSetTVRect1080;
    private static Method sIsFullScreen;

    private TvApi() {
    }

    private static boolean ensure() {
        if (sInstance != null) {
            return true;
        }
        try {
            Class<?> cls = Class.forName(CLS_SHARP_SOURCE);
            sInstance = cls.getMethod("getInstance").invoke(null);
            sGetCur = cls.getMethod("getCurSourceId");
            sGetList = cls.getMethod("getSourceList");
            sSelect = cls.getMethod("selectSource", int.class, boolean.class);
            try {
                sSetTVRect1080 = cls.getMethod("setTVRect1080");
                sIsFullScreen = cls.getMethod("isWindFullScreen");
            } catch (Throwable ignored) {
            }
            return sInstance != null;
        } catch (Throwable t) {
            Log.w(TAG, "SharpSource unavailable: " + t);
            sInstance = null;
            return false;
        }
    }

    /** 可用信号源 id 列表,拿不到返回空列表 */
    static List<Integer> getSourceIds() {
        List<Integer> out = new ArrayList<>();
        if (!ensure()) {
            return out;
        }
        try {
            Object r = sGetList.invoke(sInstance);
            if (r instanceof List) {
                for (Object o : (List<?>) r) {
                    if (o instanceof Integer) {
                        out.add((Integer) o);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getSourceList failed: " + t);
        }
        return out;
    }

    /** 当前信号源 id;API 不可用时读 Settings.System 兜底 */
    static int getCurrent(Context c) {
        if (ensure()) {
            try {
                Object r = sGetCur.invoke(sInstance);
                if (r instanceof Integer) {
                    return (Integer) r;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return Settings.System.getInt(c.getContentResolver(), "sourceid_changed", -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 把视频窗口恢复成整屏。
     *
     * 工厂菜单会把视频窗口矩形改掉用于显示测试画面,退出后不一定还原。
     * 后果很典型:Android 的 UI 层照常显示,但硬件视频层没有可见区域 ——
     * 应用里播放视频就只有声音、没有画面。
     *
     * 判定用官方的 isWindFullScreen()(内部就是判断窗口宽度 >= 1920),
     * 不要自己读 RectInfo:getWindowRect 返回的字段不可靠(实测是垃圾值)。
     */
    static void resetWindowRect() {
        if (!ensure() || sSetTVRect1080 == null || sIsFullScreen == null) {
            return;
        }
        try {
            boolean full = (Boolean) sIsFullScreen.invoke(sInstance);
            if (full) {
                return;
            }
            sSetTVRect1080.invoke(null);
            Log.d("LumaDisp", "video window was not full screen, restored to 1920x1080");
        } catch (Throwable t) {
            Log.w(TAG, "resetWindowRect failed: " + t);
        }
    }

    /**
     * 切到指定信号源。
     *
     * ATV 类信源走官方入口(见 PROP_SOURCE_IN 注释),原厂 receiver 在自己的
     * 进程里完成全部三步,我们这边没有任何时序假设;
     * DTV(1/2)走直连:DTVActivity 认 SourceName extra,而且官方
     * DTVReceiver 把源硬编码成 2,不能用于信源 1。
     */
    static boolean switchTo(Context c, int id) {
        if (PREFER_OFFICIAL_PATH && id != 1 && id != 2 && setSourceProp(id)) {
            c.sendBroadcast(new Intent(ACTION_START_ATV));
            Log.d("LumaSrc", "official path id=" + id);
            return true;
        }
        return legacySwitch(c, id);
    }

    /** 写入目标源属性并读回验证;写不进来说明官方路径不可用,返回 false */
    private static boolean setSourceProp(int id) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method set = sp.getMethod("set", String.class, String.class);
            Method get = sp.getMethod("get", String.class);
            set.invoke(null, PROP_SOURCE_IN, String.valueOf(id));
            boolean ok = String.valueOf(id).equals(get.invoke(null, PROP_SOURCE_IN));
            if (!ok) {
                Log.w(TAG, "prop " + PROP_SOURCE_IN + " not writable from this uid");
            }
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "set " + PROP_SOURCE_IN + " failed: " + t);
            return false;
        }
    }

    /**
     * 老路径:先切源,再让 TV 应用全屏接管画面。
     * 少了第二步,画面不会变(实测)。
     * 已知问题:ATVMainActivity 初始化时自己会跑两段互相竞争的切源逻辑
     * (reSelectSource),所以需要延后补一次 selectSource 才能落定——
     * 官方 receiver 路径没有这个问题,这里只作为兜底保留。
     */
    private static boolean legacySwitch(Context c, int id) {
        boolean ok = false;
        int before = getCurrent(c);
        if (ensure()) {
            try {
                Object r = sSelect.invoke(sInstance, id, true);
                ok = (r instanceof Boolean) && (Boolean) r;
            } catch (Throwable t) {
                Log.w(TAG, "selectSource(" + id + ") failed: " + t);
            }
        } else {
            Log.w(TAG, "switchTo skipped: SharpSource unavailable");
        }
        Log.d("LumaSrc", "switchTo id=" + id + " before=" + before + " ok=" + ok);
        if (!ok) {
            return false;
        }

        try {
            Intent i = new Intent();
            if (id == 1 || id == 2) {
                // 有线/地面数字电视:DTVActivity
                i.setAction("android.intent.hisiaction.HisiDTV");
                i.setComponent(new ComponentName(DTv_PKG, DTv_ACT));
            } else {
                // HDMI/AV/分量/VGA/模拟电视:原厂走 HisiATV -> ATVMainActivity
                i.setAction("android.intent.hisiaction.HisiATV");
            }
            // ATVMainActivity.reSelectSource() 第二段会按它重选目标源
            i.putExtra("SourceName", id);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            Log.d("LumaSrc", "start action=" + i.getAction() + " SourceName=" + id);

            // ATVMainActivity.reSelectSource() 第一段会把源退回上一个源,
            // 与第二段(按 SourceName)竞争,结束后才能补刀落定。
            final Context app = c.getApplicationContext();
            final int target = id;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object r = sSelect.invoke(sInstance, target, true);
                        boolean ok2 = (r instanceof Boolean) && (Boolean) r;
                        Log.d("LumaSrc", "delayed select id=" + target + " ok=" + ok2
                                + " current=" + getCurrent(app));
                    } catch (Throwable t) {
                        Log.w(TAG, "delayed selectSource(" + target + ") failed: " + t);
                    }
                }
            }, 1500L);
        } catch (Throwable t) {
            Log.w(TAG, "start TV activity failed: " + t);
        }
        return true;
    }
}
