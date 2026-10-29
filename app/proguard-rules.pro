# 桌面没有反射自己的类,但保留四大组件名避免被混淆后找不到
-keep class com.tvdesk.home.LauncherActivity { *; }
-keep class com.tvdesk.home.SourcePanelActivity { *; }
-keep class com.tvdesk.home.SourceEditActivity { *; }
-keep class com.tvdesk.home.AllAppsActivity { *; }
-keep class com.tvdesk.home.SettingsActivity { *; }
-keep class com.tvdesk.home.BootReceiver { *; }

# 通过反射调用的夏普 TV API,类在系统里,不需要额外规则
-dontwarn com.sharp.**
