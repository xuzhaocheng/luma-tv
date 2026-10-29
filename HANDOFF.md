# 拾光桌面 · 交接文档

> 更新于 2026-10-28。读这份就能接着干,不用翻聊天记录。

---

## 0. 目录约定

本仓库是一个独立的 Gradle 工程:电视桌面「拾光桌面」(Luma TV Home)。

---

## 1. 项目定位

给 **Sharp LCD-xxBEL7A-C(Android 4.4.2,API 19)** 做的极简电视桌面,替代原厂桌面。

- 中文名:**拾光桌面**
- 英文名:**Luma TV Home**
- 包名:`com.luma.home`

核心诉求(用户明确提过):
1. 桌面要能**切信号源**(原厂桌面有,当贝桌面没有,所以不接受当贝)
2. 常用信源常驻首页,可自定义名字和图标
3. 开机可以选择自动切到某个信源(比如机顶盒)
4. 纯色深背景、轻量、流畅

---

## 2. 目标设备与硬约束

| 项 | 值 |
|---|---|
| 设备 | SHARP LCD-xxBEL7A-C,1920×1080 |
| 系统 | Android 4.4.2 (API 19),纯 AOSP(不是 YunOS) |
| 内存 | 约 1.5 GB,开机后可用 300–600 MB |
| 输入 | 遥控器(方向键 + OK + 返回),无鼠标(临时插 USB 鼠标可用) |
| adb | 默认关闭;工厂菜单里 `Android Setting` 打开,端口 5555 |
| adb 持久性 | **不具备**:`service.adb.tcp.port` 是内存属性,重启即失效,每次重启都要重新开工厂菜单 |

### 折腾这台机器必须知道的坑

1. **`pm disable` 被 ROM 封死**,adbd 是 shell(uid 2000)也不行,只能通过设置界面(uid 1000)操作
2. **工厂菜单只响应按键事件**(`dispatchKeyEvent`),不响应鼠标点击
3. **「应用管家」会抢 `android.settings.APPLICATION_DETAILS_SETTINGS`**,要跳应用信息页必须用显式组件:`com.android.settings/.applications.InstalledAppDetailsTop`
4. **`screencap` 只能截到 Android 层**,截不到电视视频层 —— 切源后画面是否变化只能靠人眼看
5. **Gradle 增量打包会让 APK 虚胖**(实测 89KB 的包显示成 1.2MB),量体积前必须 `clean`

---

## 3. 已验证的技术结论(重要,别再重复验证)

### 3.1 信号源 API 可用

`/system/framework/com.sharp.android.tvapi.jar` **在 BOOTCLASSPATH 上**,任何 App 都能直接反射调用,无需系统签名。

实测(uid=10045 普通应用):

```
SharpSource.getInstance()        ✅
getCurSourceId()                 ✅ → 当前源 id
getSourceList()                  ✅ → [0,1,2,3,4,6,7,8,9,10,11,13,14]
selectSource(id, true)           ✅ → 返回 true,无 SecurityException
```

### 3.2 切源必须两步,少一步画面不变

```java
// 1. 切源(只改状态,画面不动)
SharpSource.getInstance().selectSource(id, true);

// 2. 让 TV 应用全屏接管画面 —— 关键
startActivity(new Intent().setComponent(
        new ComponentName("com.sharp.dtv", "com.sharp.dtv.DTVActivity")));
```

来源:反编译原厂桌面 `com.sharp.launcher3`,
`com.sharp.platform.utils.TvApi.switchTvInputSource()` 里这两步是连着做的。

**回桌面**:按 HOME 键即可,系统会自动把源切回 Android 并回到默认桌面。

### 3.3 源 ID 对照表

| id | 含义 | id | 含义 |
|---|---|---|---|
| 0 | 模拟电视 ATV | 9 | **HDMI1** |
| 1 | 有线 DVBC | 10 | **HDMI2** |
| 2 | 地面 DTMB | 11 | **HDMI3** |
| 3/4/5 | AV1/2/3 | 12 | HDMI4(本机无) |
| 6 | VGA | 13 | 媒体 |
| 7/8 | 分量1/2 | 14/15 | SCART1/2 |
| | | 16 | 自动 |

本机实际可用:`0,1,2,3,4,6,7,8,9,10,11,13,14`(13 个)

**注意**:处于 Android 桌面态时 `getCurSourceId()` 返回 **22**,但 22 **不是合法的切换目标**(`selectSource(22)` 返回 false)。回 Android 态用 HOME 键,或者 `SharpSource.changeToMedia()`(内部是 `deselectSource(cur,true)` + `selectSource(13,0)`)。

---

## 4. 代码结构

```
app/src/main/java/com/luma/home/
├── LauncherActivity.java     主页:顶栏固定,下方整体可滚动(信源 → 托盘 → 应用网格)
├── SourcePanelActivity.java  全部信源(网格),点即切源,长按编辑
├── SourceEditActivity.java   编辑信源:预设名字 / 自定义名字 / 图标 / 设为常用
├── SettingsActivity.java     设置:系统(工厂菜单)、首页入口、外观、关于
├── AppDialogs.java           长按应用的菜单 + 固定位满时的替换确认
├── TvApi.java                夏普 TV API 的反射封装(含两步切源、窗口矩形自愈)
├── TvScrollView.java         电视用 ScrollView:滚动时在焦点下方多留 90dp
├── SourceStore.java          信源配置持久化 + 默认名字/图标 + 4 个常用位上限
├── SourceItem.java           信源数据模型
├── AppRepo.java              应用列表读取(含图标栅格化)+ 首次自动填充
├── AppStore.java             首页固定应用持久化(6 个位上限)
├── IconLib.java              图标库:10 个设备图标
├── Backgrounds.java          6 套内置背景配色
├── Prefs.java                开关与配置:焦点大字、状态行、背景、媒体应用
└── BootReceiver.java         开机后延时切源(8s / 20s 两次)
```

资源:
- `res/layout/` —— 主页 / 信源面板 / 编辑 / 全部应用 / 设置 / 列表项
- `res/drawable/ic_dev_*.xml` —— 10 个设备图标(矢量,构建时栅格化)
- `res/anim/` —— 全部应用上滑/下滑动画
- `res/values/`(英文默认)+ `res/values-zh/`(中文)

---

## 5. 已实现的功能

| 功能 | 状态 | 说明 |
|---|---|---|
| 常用信源 4 张卡片常驻首页 | ✅ | 可折叠入口:顶栏「信源」按钮进面板 |
| 切信号源 | ✅ | `TvApi.switchTo()` 两步走;当前源高亮 |
| 自定义信源名字 | ✅ | 9 个预设(机顶盒/PS5/Switch/Xbox/电脑/蓝光/投影/音响/摄像头)+ 手动输入 |
| 自定义信源图标 | ✅ | 10 个设备图标 |
| 信源设为常用 / 取消 | ✅ | 满 4 个时弹确认框替换最早固定的 |
| 应用 Dock 6 格 | ✅ | 固定的在前(带 ★),不够用全部应用补齐 |
| 全部应用 | ✅ | Dock 按「下」滑上来;第一排按「上」或返回键收起 |
| 长按应用 | ✅ | 固定/取消固定、应用信息、卸载(系统应用不显示卸载) |
| 固定位满时替换 | ✅ | 弹确认框,文案带被替换者名字 |
| 首次自动填充 | ✅ | 按安装时间取最近安装的 6 个第三方应用 |
| 焦点大字 | ✅ | 26sp,可在设置关闭 |
| 状态行 | ✅ | 可用内存 + 可用存储,20 秒刷新,可在设置关闭 |
| 背景配色 | ✅ | 6 套,设置 → 外观 → 背景 |
| ~~开机默认信源~~ | 已删除 | 用原厂 Sharp 菜单的全局设置;我们只提供「设置 → 系统 → 工厂菜单」入口 |
| 中英双语 | ✅ | 连信源名、图标名都本地化 |
| 默认桌面 | ✅ | 注册了 HOME,装好后按 HOME 选「始终」 |

---

## 6. 关键设计决策(以及为什么)

1. **不用 androidx / AppCompat**
   一个它的控件都没用,却占 1.9MB dex + 300KB 资源,还拖慢启动。
   换成 `android.app.Activity` + 系统主题后:**2.1MB → 72KB(release)**。
   ⚠️ 副作用:弹窗变成系统 Holo 深色样式,和自定义界面有视觉差异,真机上确认是否要换自定义弹窗。

2. **Dock 固定 6 格,不滚动**
   遥控器只有方向键,滚动找应用很痛苦;6 格一屏看全,位置可预期。

3. **不设「全部应用」入口格,改为按「下」展开**
   参考 CleanYourTV / Aerial:它们都没有入口格,靠"下方就是全部"的空间直觉。
   原方案(第 6 格放「全部」)是我造出来的补偿设计,已废弃。

4. **固定位满 → 替换最早的 + 确认框**
   不禁止,也不静默丢弃,给用户一次确认。

5. **背景只做内置配色,不做自定义图片**
   一张 1920×1080 图 ARGB_8888 解码就是 8.3MB,4K 图会直接 OOM。
   等真机跑稳、确实需要再考虑(需要采样解码 + 裁切 + 压缩)。

6. **开机切源用 AlarmManager 延时,而不是立刻切**
   `BOOT_COMPLETED` 时 TV 服务还没就绪,立刻切会失败。用 `ELAPSED_REALTIME` 避免 NTP 校时跳变。

7. **图标栅格化到 96×96**
   系统返回的图标常是 512×512,25 个应用就是几十 MB;压到 96px 后总量约 900KB。

---

## 7. 构建与安装

```bash
cd <repo>

# 调试包(体积约 89KB,需 clean 后测量才准)
JAVA_HOME=$JDK /path/to/gradle :app:clean :app:assembleDebug

# 发布包(R8 压缩 + debug 签名,可直接安装,约 72KB)
JAVA_HOME=$JDK /path/to/gradle :app:assembleRelease

# 安装
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.luma.home/.LauncherActivity
```

工具链:AGP 7.4.2 + Gradle 7.6.2 + JDK 11 以上,编译 SDK 33。

---

## 8. 今晚开机后要验证的清单(2026-10-28)

前提:电视重启后 **adb 会失效**,需重新进工厂菜单 → `Android Setting` → 让状态变 `OPEN`。

```
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s TvDeskSrc TvApi
```

### UI(tvOS 风格:胶囊 + 托盘)

1. 顶栏「信源/媒体库/设置」包在一个半透明胶囊里;焦点的那个图标变实心变蓝,
   且所在段有更亮的底色(无描边)。若觉得段高亮多余:删 `bg_nav_segment.xml` 里
   `state_focused` 的 item 即可
2. 底部固定应用包在一个托盘胶囊里(`bg_tray.xml`);确认卡片四角没有顶出托盘,
   焦点放大 1.06 时不被裁
3. 只固定 1~2 个应用时,托盘应跟着收窄,不拖满整行

### 氛围光(焦点变色)

4. 焦点移到不同信源卡 → 背景微微染上对应色(电视=蓝、HDMI=青、媒体=品红、
   游戏机=暖橙…);焦点离开后 320ms 内熄掉
5. 焦点移到应用卡 → 背景染上该应用图标的平均色;顶栏三个入口也各有一个色
6. 觉得太淡/太浓 → 改 `LauncherActivity.java:52` 的 `AMBIENT_ALPHA`(0x00~0xFF)

### 全部应用刷新

7. 安装或删除一个应用 → 回桌面、按「下」进全部应用,两处列表是否都已更新

### 三条 review 改进(2026-10-28)

8. 未聚焦卡片压暗到 0.8(信源卡/应用槽/引导卡/全部应用网格项),焦点卡全亮;
   感觉太暗改 `LauncherActivity` 的 `CARD_DIM`
9. 底部 RAM/存储状态行默认关闭了,设置里可以再打开
10. 设置 → 首页 → 「媒体应用」:把顶栏媒体按钮指到任意应用,默认仍是夏普播放器;
    选中的应用被卸载后按钮自动回落默认
11. 弹窗(长按应用菜单、设置里的对话框)外圈不再有直角深色背景,
    应该是标准 Holo 圆角框 + 投影。如果嫌框体灰色(#2A2A2A)和我们的
    卡片色(#1B2129)不搭、或圆角太小,可以走自定义 alertDialogStyle,
    但要按 topFullDark/centerFullDark/bottomFullDark 分区做拼接图,
    盲写有接缝风险,看过实际效果再决定

### 信源切换(今晚的重点!)

12. 逐个切换所有常用信源,特别是 **HDMI ↔ 媒体(13) 来回切**,确认都能正确落定
13. 看 logcat:
    - 出现 `official path id=xx` → 走了官方 ATVReceiver 入口(新路径,无延时补刀)
    - 出现 `prop launcher_sourcein not writable` → 属性写不进去,已自动回退老路径
    - 若官方路径切错源/切不动 → 把 `TvApi.java:39` 的 `PREFER_OFFICIAL_PATH`
      改为 `false` 回退老路径(老路径 = select + startActivity + 1.5s 延时补刀)
14. DTV(信源 1/2)不走官方入口(官方 DTVReceiver 把源硬编码成 2),单独验证一次

### 实测结果(2026-10-28 晚)

- **UI 全部通过**:顶栏胶囊 + 焦点段、底部托盘、卡片压暗、氛围光
  (奇异果=绿/HDMI=蓝/游戏=暖橙,焦点离开熄灭)、弹窗圆角无外圈 —— 均符合预期
- **官方路径确认不可用**:`launcher_sourcein` 普通 uid 写不进(实测日志),
  写不进 ATVReceiver 会选到源 0。代码自动回退老路径,行为正确;
  想彻底走官方路径需要 root,放弃
- **老路径实测两连切正确**:13→10(HDMI2)、10→9(HDMI1),
  1.5s 补刀后 `current` 均等于目标源,delay 目前仍是必要的
- 装/删应用刷新网格的实测,留待下次自然安装时观察
- **默认桌面仍是原厂 launcher3**(按 HOME 会回到原厂桌面),
  需要在电视上按 HOME → 选「拾光桌面」→ 始终

### 开机默认信源:功能已删除(2026-10-28,用户决定)

原厂 Sharp 菜单里已有开机信源设置,且是全局生效的,我们不再自己实现。
已删除:设置行、选择对话框、`Prefs.autoSource`、`TvApi.setBootSource/getBootSourceId`、
`BootReceiver`/`TvHomeReceiver`、`RECEIVE_BOOT_COMPLETED` 权限。

替代:设置 → **关于 → 上一个分区「系统」→ 工厂菜单**,直接打开
`com.android.FactoryAdjust/.FactoryActivity`(原厂工厂菜单,含开机信源、面板等)。
**工厂菜单是打开 ADB 的地方**(重启后要连 adb 就走这里)。

历史教训(供参考):自己实现开机切源踩过的坑——
- AlarmManager 方案在这台快启电视上是灾难(每次通电都发 QUICKBOOT_POWERON,
  用户按 HOME 回桌面会被反复拽走);
- 原厂链路是 `FxcSharpServiceReceiver` → `SettingService.goTv()` → 读
  `sys.sharp.boot.source` → selectSource + startActivity,且 com.sharp.settings
  在工厂模式下被禁用(enabled=2),此时整条链路失效。

焦点视觉最终参数(用户确认不闪):氛围光 20% 只在应用行 + 1s 熄灯延迟、
压暗 0.9、缩放 180ms;信源卡/顶栏不参与氛围光。

---

## 8b. 信源切换的技术结论(2026-10-28 逆向所得)

`ATVMainActivity.reSelectSource()` 内部有两段互相竞争的切源逻辑
(第一段把源退回"上一个源",第二段按 Intent 的 SourceName 重选),
这就是老路径需要 1.5s 延时补刀的原因。

官方真正的入口在 `com.sharp.atv/.manager.ATVReceiver`:

```
发广播 Action_Start_ATV(无权限保护,普通应用可发)
receiver 内部:读系统属性 launcher_sourcein → setTVRect1080()
             → deselectSource(当前源) → selectSource(目标源)
             → startActivity(ATVMainActivity)
```

三步在 ATV 自己的进程里按序完成,没有跨进程竞争,所以新路径不需要任何延时。
`sourceid_changed` 只是开/关机握手标志位,不是"信源落定"事件,ContentObserver
方案不可行;`SharpSource` / 海思 `SourceManager` 均无 listener 接口。
另:`SystemProperties.get("persist.sys.bootup") == "1"` 时 ATV 认为是开机流程。

---

## 8c. 工厂菜单 CTS SETTINGS 会导致视频黑屏(2026-10-28)

现象:在工厂菜单里打开 **CTS SETTINGS** 后,所有应用播放视频只有声音没有画面
(Android UI 正常,只有硬件视频层黑),退出工厂菜单也不恢复。

根因:`persist.sys.cts.enable` 被置为 `true`(伴生只读属性 `ro.cts.enable=true`)。
CTS 模式会改视频通路,视频层不再合成。

处理:**在工厂菜单里把 CTS SETTINGS 关掉**,或关闭工厂模式。shell 的
`setprop persist.sys.cts.enable false` 被属性权限拦住,不要指望命令行改。

实测到的时序规律(2026-10-28):
- 工厂模式开着但**没重启** → 关掉工厂模式,视频立刻恢复;
- 工厂模式开着**重启过** → 之后关工厂模式,视频回不来(视频通路在启动时
  已按工厂/CTS 状态初始化好了)。
  推测的解法(未验证):关掉 CTS/工厂模式后**再重启一次**,让下次启动读到
  正常状态;都不行才需要恢复出厂。

**2026-10-28 晚已恢复出厂**(用户操作),设备回到干净状态。教训:工厂菜单里的
CTS SETTINGS 不要碰。

补充:视频窗口矩形不是原因 —— 实测 `isWindFullScreen()` 一直是 true。
`TvApi.resetWindowRect()` 保留为兜底(仅在 `isWindFullScreen()` 为 false 时
才调用 `setTVRect1080()`),平时是空操作。

---

## 8d. 默认桌面切换(2026-10-28)

Android 4.4 不允许应用直接改默认桌面。当前实现:

- 「清拾光默认」= `PackageManager.clearPackagePreferredActivities(自己包名)`,
  **不依赖系统设置页**。系统只允许应用清自己的默认,不需要任何权限。
- 弹窗只剩两个按钮(取消 / 清拾光默认)。原来还有个「清原厂默认」,已删除:
  它要跳 `com.android.settings` 的应用信息页,而正常模式下这个包被
  com.sharp.settings 顶替、根本不存在,通用 action 又会被应用管家劫持,
  必然无效;清别人的默认本来也不被允许。
- 切换流程:清默认 → 按 HOME → 选择桌面 → 始终。

踩坑备忘:工厂模式下 `com.android.settings` 存在,所以"跳应用信息页"这条路
在工厂模式里是好的,一关工厂模式就失效 —— 排查时容易被这个假象误导。

---

## 9. 后续计划

**第一批(已完成)**:信源模块、应用模块、设置页、双语、瘦身、背景配色

**第二批(待做,按优先级)**
1. **时钟屏保**(DreamService):防烧屏,待机时显示漂移时钟 —— 参考 CleanYourTV
2. 应用 "移动位置"(长按菜单里加排序),现在只能固定/取消
3. 单排/双排 Dock 可配置(Aerial 有这个)

**暂不做**
- 自定义图片背景(见设计决策 5)
- 手机扫码整理(Aerial 的做法,工作量是当前 2-3 倍)
- 文件夹、壁纸、天气、应用内自升级

---

## 10. 外部参考

| 项目 | 借鉴了什么 |
|---|---|
| [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) | Dock + 下方网格的布局;焦点大字;状态行;时钟屏保;精简时"先装好新桌面再禁用原桌面"的安全顺序 |
| [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) | 信号源按"接的什么设备"配图标;按「下」看全部应用;长按菜单;单排/双排可配置 |

⚠️ Aerial 明确**不支持 Android 4.4 及更老**,只能参考设计,代码不能直接用。

---

## 11. 设备当前状态(2026-10-28 深夜)

**已恢复出厂**,以下都待重建:

```
电视 <TV_IP>
├── adb 5555        关闭 → 工厂菜单 → Android Setting 打开
├── com.luma.home 已被清除,需重新安装
│   app/build/outputs/apk/debug/app-debug.apk
├── 应用管家            已删除
├── 工厂模式            已关闭(注意:别碰里面的 CTS SETTINGS)
└── 原厂桌面            com.sharp.launcher3 是默认桌面
```

重新设置清单:
1. 工厂菜单 → Android Setting → ADB OPEN
2. `adb install -r app-debug.apk`
3. 按 HOME → 选「拾光桌面」→ 始终
4. 设置 → 首页:重新固定常用信源和应用(配置已随恢复出厂清空)
5. 开机信源:工厂菜单里设(设完别碰 CTS SETTINGS)
