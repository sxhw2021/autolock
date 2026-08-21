# 自动锁屏 App 设计规格

日期：2026-08-21
状态：已批准

## 1. 背景与目标

用户的手机为 ColorOS 16（Android 16，已 root，装有 KernelSU 与 LSPosed）。游戏通过
`FLAG_KEEP_SCREEN_ON` / WakeLock 保持常亮，系统自动锁屏失效；用户睡着后手机亮屏一整夜。

**目标**：开发一个 Android 应用，在设定的分钟数内检测不到任何屏幕触摸操作时，
无论前台是什么应用（含游戏），立即锁屏熄屏。

**成功标准**：

1. 在任意应用（含游戏）内，无触摸达到设定时长后屏幕自动熄灭锁定
2. 每次触摸都会重置倒计时
3. 用户可自由输入 1–720 分钟的超时时长
4. APK 通过 GitHub Actions 自动构建，可直接侧载安装

## 2. 非目标

- 不做应用白名单/黑名单（用户明确选择纯全局模式）
- 不做锁屏前倒计时提醒
- 不做 LSPosed 模块（hook ColorOS 杀后台机制收益不成比例）
- 不做开机自启管理（无障碍服务由系统在重启后自动恢复绑定）

## 3. 技术方案

**方案：无障碍服务一体化 + root 兜底（已批准）**

- **触摸检测**：`AccessibilityService` 订阅 `TYPE_TOUCH_INTERACTION_START`
  全局事件。每次用户触摸任意界面，系统派发该事件，服务刷新 `lastTouchTime`。
- **倒计时**：服务内 `Handler` 每 5 秒轮询 `now - lastTouchTime ≥ timeout`。
- **锁屏双通道**：
  1. 首选 `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`（API 28+，无需设备管理员）
  2. 返回 `false` 时自动回退 root 命令 `su -c "input keyevent KEYCODE_POWER"`
  3. 两路都失败则记录日志放弃，等待下一轮询周期重试

选择理由：单一权限体系（仅无障碍）；触摸检测精确到每次点击；锁屏动作系统级可靠；
root 兜底进一步提升可靠性且对用户零配置。

## 4. 组件设计

| 组件 | 类型 | 职责 | 接口/依赖 |
|------|------|------|-----------|
| `AutoLockService` | AccessibilityService | 监听触摸、维护倒计时、执行双通道锁屏、监听亮熄屏广播 | 读 PrefsRepository |
| `MainActivity` | Activity | 状态卡片、总开关、时长输入、保活指引 | PrefsRepository、ServiceStatusChecker |
| `PrefsRepository` | 单例封装 | SharedPreferences 读写：`timeout_minutes`(Int, 默认5)、`enabled`(Boolean, 默认true) | Context |
| `ServiceStatusChecker` | 工具对象 | 通过 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 判断本服务是否已启用 | Context |

## 5. 核心流程与边界处理

```
onServiceConnected → 读配置 → lastTouchTime = now → 注册亮/熄屏 receiver → 启动轮询
onAccessibilityEvent(TYPE_TOUCH_INTERACTION_START) → lastTouchTime = now
轮询(每5s)：enabled && screenOn && now-lastTouchTime ≥ timeout → lock()
lock()：performGlobalAction 成功？→ 结束；失败 → su -c input keyevent 26
ACTION_SCREEN_OFF → 暂停轮询
ACTION_SCREEN_ON → lastTouchTime = now，恢复轮询
onUnbind / onDestroy → 移除回调、注销 receiver
```

边界规则：

1. 熄屏期间不计时；亮屏瞬间重置计时起点，避免"亮屏即被锁"
2. 总开关关闭时服务保持运行但永不执行锁屏
3. 时长输入校验 1–720，非法输入回退上次有效值并在 UI 提示
4. ColorOS 杀后台/关无障碍：MainActivity 每次 `onResume` 实时检测服务状态，
   未启用则红色高亮并提供一键跳转系统无障碍设置页；页面附 ColorOS 保活指引文案
   （电池允许后台高耗电、最近任务加锁）

## 6. UI 规格（单页面，XML + Material Components）

```
┌─────────────────────────────┐
│ 状态卡片                     │
│ 无障碍服务：● 已启用/✗ 未开启 │ ← 未开启: 红色 + [去开启] 按钮
├─────────────────────────────┤
│ 自动锁屏总开关      [Switch] │
├─────────────────────────────┤
│ 超时时长 [  5 ] 分钟  [保存] │
├─────────────────────────────┤
│ 使用说明与 ColorOS 保活指引   │
└─────────────────────────────┘
```

## 7. 技术栈与构建

- Kotlin 2.0.x，minSdk 28（GLOBAL_ACTION_LOCK_SCREEN 最低要求），targetSdk/compileSdk 35
- AGP 8.7.x + Gradle 8.9 + JDK 17；依赖仅 core-ktx、appcompat、material
- 应用包名 `com.demo.autolock`，应用名"自动锁屏"
- 无障碍服务配置：`accessibilityEventTypes="typeTouchInteractionStart"`，
  `canRetrieveWindowContent="false"`（不读取屏幕内容，隐私友好），
  `accessibilityFeedbackType="feedbackGeneric"`

## 8. 构建与交付（GitHub Actions）

- `.github/workflows/android-build.yml`：push 到 main 及手动触发
- ubuntu-latest + setup-java(temurin 17) + gradle/actions/setup-gradle
- 执行 `./gradlew assembleDebug --stacktrace`，上传 `app-debug.apk` 为 artifact；
  打 `v*` tag 时额外创建 GitHub Release 并附带 APK
- README.md：功能说明、开启无障碍步骤、ColorOS 保活指引、Actions 下载 APK 教程

## 9. 测试策略

本地无 Android SDK，编译验证依赖 CI：

1. CI 构建通过 = 编译级验证
2. 真机验收清单（README 附带）：
   - 开启无障碍后状态卡片变绿
   - 设 1 分钟，静置桌面 1 分钟后自动熄屏
   - 游戏内（保持亮屏场景）静置同样触发
   - 触摸后倒计时重置（59 秒时触摸不会在 1 秒后熄屏）
   - 关闭总开关后不再触发
   - 熄屏后再亮屏不会被立即锁定
