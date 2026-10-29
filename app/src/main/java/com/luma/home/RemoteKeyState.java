package com.luma.home;

/**
 * Activity 与 AccessibilityService 同进程共享的临时状态。
 *
 * 配置页可见时,按键全部放行,避免用户在学习/操作界面时被快捷键抢走。
 */
final class RemoteKeyState {

    static volatile boolean activityVisible;

    private RemoteKeyState() {
    }
}
