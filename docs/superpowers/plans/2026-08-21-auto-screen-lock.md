# 自动锁屏 App 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** Android 应用——任意界面（含游戏）无触摸达到设定分钟数即自动锁屏，支持 root 兜底锁屏，APK 由 GitHub Actions 构建。

**架构：** 无障碍服务监听全局触摸事件刷新时间戳，Handler 每 5 秒轮询超时后执行双通道锁屏（GLOBAL_ACTION_LOCK_SCREEN → su 命令兜底）。单 Activity 设置页管理开关与时长。

**技术栈：** Kotlin 2.0.21、AGP 8.7.3、Gradle 8.9、JDK 17、minSdk 28 / targetSdk 35、Material Components、JUnit4（JVM 单元测试）

**验证环境约束：** 本机无 Android SDK。所有编译与测试验证通过 GitHub Actions 完成（`./gradlew test assembleDebug`）。每个任务的"验证"步骤为静态检查，最终验证集中在任务 8 的 CI 触发。

---

## 文件结构

```
D:\demo\自动锁屏\
├── .github/workflows/android-build.yml   # CI：test + assembleDebug + artifact/release
├── .gitignore                            # Android 标准忽略
├── README.md                             # 使用说明 + ColorOS 保活指引 + 真机验收清单
├── settings.gradle                       # 仓库与模块声明
├── build.gradle                          # 根插件声明
├── gradle.properties                     # AndroidX/JVM 参数
├── gradle/wrapper/gradle-wrapper.properties + gradle-wrapper.jar
├── gradlew, gradlew.bat                  # 从 gradle 官方仓库获取
└── app/
    ├── build.gradle                      # 应用模块配置
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       # Activity + 无障碍服务声明
        │   ├── res/
        │   │   ├── drawable/ic_launcher.xml          # 矢量启动图标
        │   │   ├── layout/activity_main.xml          # 设置页布局
        │   │   ├── values/strings.xml, colors.xml, themes.xml
        │   │   └── xml/accessibility_service_config.xml
        │   └── java/com/demo/autolock/
        │       ├── PrefsRepository.kt     # 设置持久化（时长、总开关）
        │       ├── TimeoutValidator.kt    # 时长校验纯函数（可单测）
        │       ├── ServiceStatusChecker.kt# 无障碍启用状态检测
        │       ├── AutoLockService.kt     # 核心服务：触摸检测/倒计时/双通道锁屏
        │       └── MainActivity.kt        # 设置界面
        └── test/java/com/demo/autolock/
            └── TimeoutValidatorTest.kt    # 校验逻辑单元测试
```

---

### 任务 1：项目骨架（Gradle 配置 + CI 工作流）

**文件：**
- 创建：`.gitignore`、`settings.gradle`、`build.gradle`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`、`app/build.gradle`、`.github/workflows/android-build.yml`

- [ ] **步骤 1：创建 `.gitignore`**

```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
captures/
.externalNativeBuild/
.cxx/
```

- [ ] **步骤 2：创建 `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AutoLock"
include ':app'
```

- [ ] **步骤 3：创建根 `build.gradle`**

```groovy
plugins {
    id 'com.android.application' version '8.7.3' apply false
    id 'org.jetbrains.kotlin.android' version '2.0.21' apply false
}
```

- [ ] **步骤 4：创建 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **步骤 5：创建 `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **步骤 6：创建 `app/build.gradle`**

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.demo.autolock'
    compileSdk 35

    defaultConfig {
        applicationId "com.demo.autolock"
        minSdk 28
        targetSdk 35
        versionCode 1
        versionName "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **步骤 7：创建 `.github/workflows/android-build.yml`**

```yaml
name: Android CI

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build & Test
        run: |
          chmod +x gradlew
          ./gradlew test assembleDebug --stacktrace

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: autolock-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk

  release:
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    needs: build
    steps:
      - name: Download APK
        uses: actions/download-artifact@v4
        with:
          name: autolock-debug-apk
          path: apk/

      - name: Create Release
        uses: softprops/action-gh-release@v2
        with:
          files: apk/app-debug.apk
```

注意：release job 中 download-artifact 需要跨 job 传递，artifact 在 build job 上传，
download-artifact@v4 默认可取同一 workflow run 内任意 job 的 artifact，无需额外配置。

- [ ] **步骤 8：Commit**

```bash
git add .gitignore settings.gradle build.gradle gradle.properties gradle/ app/build.gradle .github/
git commit -m "chore: Gradle 项目骨架与 GitHub Actions 构建工作流"
```

---

### 任务 2：获取 Gradle Wrapper 二进制

**文件：**
- 创建：`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`

- [ ] **步骤 1：从 Gradle 官方仓库 v8.9.0 标签下载三个文件**

```powershell
$base = "https://raw.githubusercontent.com/gradle/gradle/v8.9.0"
Invoke-WebRequest -Uri "$base/gradlew" -OutFile "gradlew"
Invoke-WebRequest -Uri "$base/gradlew.bat" -OutFile "gradlew.bat"
Invoke-WebRequest -Uri "$base/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle/wrapper/gradle-wrapper.jar"
```

预期：三个文件下载成功，jar 约 43KB。
若网络失败：改用 `https://github.com/gradle/gradle/raw/v8.9.0/...` 重试；
仍失败则删除 CI 中 `chmod +x gradlew && ./gradlew` 行，改为 `gradle test assembleDebug --stacktrace`
（setup-gradle 已提供系统级 gradle），并在计划外记录该偏差。

- [ ] **步骤 2：验证 jar 为有效 zip（PK 头）**

```powershell
$bytes = [System.IO.File]::ReadAllBytes("gradle/wrapper/gradle-wrapper.jar")[0..1]
[System.Text.Encoding]::ASCII.GetString($bytes)  # 预期输出 "PK"
```

- [ ] **步骤 3：Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar
git commit -m "chore: 添加 Gradle Wrapper 8.9"
```

---

### 任务 3：资源与清单

**文件：**
- 创建：`app/src/main/AndroidManifest.xml`、`res/values/strings.xml`、`res/values/colors.xml`、`res/values/themes.xml`、`res/xml/accessibility_service_config.xml`、`res/drawable/ic_launcher.xml`、`res/layout/activity_main.xml`

- [ ] **步骤 1：创建 `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">自动锁屏</string>
    <string name="a11y_summary">检测屏幕触摸，超时未操作自动锁屏</string>
    <string name="a11y_description">本服务仅用于检测您是否正在触摸屏幕（不读取任何屏幕内容），超过设定时长无触摸时自动关闭屏幕并锁定，防止游戏等常亮应用导致手机整夜亮屏。</string>
    <string name="status_enabled">无障碍服务：已启用</string>
    <string name="status_disabled">无障碍服务：未开启</string>
    <string name="btn_enable_accessibility">去开启</string>
    <string name="master_switch">自动锁屏总开关</string>
    <string name="timeout_label">超时时长（分钟，1–720）</string>
    <string name="btn_save">保存时长</string>
    <string name="toast_saved">已保存：%1$s 分钟</string>
    <string name="toast_invalid">输入无效，请输入 1–720 的整数</string>
    <string name="guide_title">ColorOS 保活指引</string>
    <string name="guide_body">1. 设置 → 电池 → 更多设置 → 找到「自动锁屏」→ 允许后台高耗电\n2. 最近任务卡片下拉 → 锁定「自动锁屏」\n3. 若无障碍开关被系统自动关闭，请回到本页面重新开启\n4. 手机已 root 时锁屏失败会自动尝试 su 命令兜底</string>
</resources>
```

- [ ] **步骤 2：创建 `res/values/colors.xml` 与 `themes.xml`**

```xml
<!-- colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="status_ok">#1B873B</color>
    <color name="status_bad">#C62828</color>
</resources>
```

```xml
<!-- themes.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.AutoLock" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

- [ ] **步骤 3：创建 `res/xml/accessibility_service_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeTouchInteractionStart"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="false"
    android:summary="@string/a11y_summary"
    android:description="@string/a11y_description" />
```

- [ ] **步骤 4：创建 `res/drawable/ic_launcher.xml`（矢量锁形图标）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#1E88E5" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#FFFFFF" android:pathData="M54,30c-6.6,0 -12,5.4 -12,12v6h-4c-2.2,0 -4,1.8 -4,4v20c0,2.2 1.8,4 4,4h32c2.2,0 4,-1.8 4,-4V52c0,-2.2 -1.8,-4 -4,-4h-4v-6c0,-6.6 -5.4,-12 -12,-12zM54,36c3.3,0 6,2.7 6,6v6h-12v-6c0,-3.3 2.7,-6 6,-6zM54,58c2.2,0 4,1.8 4,4s-1.8,4 -4,4 -4,-1.8 -4,-4 1.8,-4 4,-4z" />
</vector>
```

- [ ] **步骤 5：创建 `res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/tvStatus"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:text="@string/status_disabled"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:textColor="@color/status_bad" />

                <Button
                    android:id="@+id/btnEnable"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/btn_enable_accessibility" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/swEnabled"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/master_switch"
                    android:textSize="16sp" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/timeout_label" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <EditText
                        android:id="@+id/etTimeout"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="wrap_content"
                        android:inputType="number"
                        android:maxLength="3" />

                    <Button
                        android:id="@+id/btnSave"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:text="@string/btn_save" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/guide_title"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="@string/guide_body"
                    android:textSize="14sp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</ScrollView>
```

- [ ] **步骤 6：创建 `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AutoLock">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".AutoLockService"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

- [ ] **步骤 7：Commit**

```bash
git add app/src/main
git commit -m "feat: 清单、资源与设置页布局"
```

---

### 任务 4：设置持久化与校验逻辑（含单元测试）

**文件：**
- 创建：`app/src/main/java/com/demo/autolock/TimeoutValidator.kt`
- 测试：`app/src/test/java/com/demo/autolock/TimeoutValidatorTest.kt`
- 创建：`app/src/main/java/com/demo/autolock/PrefsRepository.kt`

- [ ] **步骤 1：编写单元测试 `TimeoutValidatorTest.kt`**

```kotlin
package com.demo.autolock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeoutValidatorTest {

    @Test fun `valid lower bound`() {
        assertEquals(1, TimeoutValidator.parse("1"))
    }

    @Test fun `valid upper bound`() {
        assertEquals(720, TimeoutValidator.parse("720"))
    }

    @Test fun `valid middle value trims whitespace`() {
        assertEquals(15, TimeoutValidator.parse(" 15 "))
    }

    @Test fun `zero rejected`() {
        assertNull(TimeoutValidator.parse("0"))
    }

    @Test fun `above max rejected`() {
        assertNull(TimeoutValidator.parse("721"))
    }

    @Test fun `negative rejected`() {
        assertNull(TimeoutValidator.parse("-5"))
    }

    @Test fun `non numeric rejected`() {
        assertNull(TimeoutValidator.parse("abc"))
    }

    @Test fun `empty rejected`() {
        assertNull(TimeoutValidator.parse(""))
    }
}
```

- [ ] **步骤 2：实现 `TimeoutValidator.kt`**

```kotlin
package com.demo.autolock

object TimeoutValidator {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 720

    /** 合法返回分钟数，非法返回 null */
    fun parse(raw: String): Int? {
        val n = raw.trim().toIntOrNull() ?: return null
        return if (n in MIN_MINUTES..MAX_MINUTES) n else null
    }
}
```

- [ ] **步骤 3：实现 `PrefsRepository.kt`**

```kotlin
package com.demo.autolock

import android.content.Context

class PrefsRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var timeoutMinutes: Int
        get() = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    companion object {
        const val KEY_TIMEOUT = "timeout_minutes"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_TIMEOUT_MINUTES = 5
    }
}
```

- [ ] **步骤 4：Commit（测试随实现一起提交，CI 中执行）**

```bash
git add app/src/main/java/com/demo/autolock/TimeoutValidator.kt app/src/main/java/com/demo/autolock/PrefsRepository.kt app/src/test
git commit -m "feat: 时长校验与设置持久化（含单元测试）"
```

---

### 任务 5：无障碍状态检测与核心锁屏服务

**文件：**
- 创建：`app/src/main/java/com/demo/autolock/ServiceStatusChecker.kt`
- 创建：`app/src/main/java/com/demo/autolock/AutoLockService.kt`

- [ ] **步骤 1：实现 `ServiceStatusChecker.kt`**

```kotlin
package com.demo.autolock

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object ServiceStatusChecker {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AutoLockService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (item in splitter) {
            if (item.equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
```

- [ ] **步骤 2：实现 `AutoLockService.kt`**

```kotlin
package com.demo.autolock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class AutoLockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastTouchTime = 0L
    @Volatile private var screenOn = true
    private var receiverRegistered = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkAndLock()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(pollRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    lastTouchTime = SystemClock.elapsedRealtime()
                    startPolling()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastTouchTime = SystemClock.elapsedRealtime()
        screenOn = true
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            lastTouchTime = SystemClock.elapsedRealtime()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun startPolling() {
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun checkAndLock() {
        val prefs = PrefsRepository(this)
        if (!prefs.enabled || !screenOn) return
        val elapsed = SystemClock.elapsedRealtime() - lastTouchTime
        if (elapsed >= prefs.timeoutMinutes * 60_000L) {
            lockScreen()
        }
    }

    private fun lockScreen(): Boolean {
        val ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (ok) return true
        Log.w(TAG, "performGlobalAction 失败，尝试 root 兜底")
        return lockViaRoot()
    }

    private fun lockViaRoot(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "input keyevent 26").start()
        val success = process.waitFor() == 0
        process.destroy()
        success
    } catch (e: Exception) {
        Log.w(TAG, "root 锁屏失败", e)
        false
    }

    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
    }

    companion object {
        private const val TAG = "AutoLockService"
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/demo/autolock/ServiceStatusChecker.kt app/src/main/java/com/demo/autolock/AutoLockService.kt
git commit -m "feat: 无障碍服务——触摸计时与双通道自动锁屏"
```

---

### 任务 6：设置界面 MainActivity

**文件：**
- 创建：`app/src/main/java/com/demo/autolock/MainActivity.kt`

- [ ] **步骤 1：实现 `MainActivity.kt`**

```kotlin
package com.demo.autolock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsRepository(this)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val swEnabled = findViewById<SwitchMaterial>(R.id.swEnabled)
        val etTimeout = findViewById<EditText>(R.id.etTimeout)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val cardStatus = findViewById<MaterialCardView>(R.id.cardStatus)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        swEnabled.isChecked = prefs.enabled
        swEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
        }

        etTimeout.setText(prefs.timeoutMinutes.toString())
        btnSave.setOnClickListener {
            val parsed = TimeoutValidator.parse(etTimeout.text.toString())
            if (parsed == null) {
                Toast.makeText(this, getString(R.string.toast_invalid), Toast.LENGTH_SHORT).show()
                etTimeout.setText(prefs.timeoutMinutes.toString())
            } else {
                prefs.timeoutMinutes = parsed
                Toast.makeText(
                    this, getString(R.string.toast_saved, parsed.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        cardStatus.setOnClickListener { refreshStatus(tvStatus, btnEnable) }
        refreshStatus(tvStatus, btnEnable)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(findViewById(R.id.tvStatus), findViewById(R.id.btnEnable))
    }

    private fun refreshStatus(tvStatus: TextView, btnEnable: Button) {
        val enabled = ServiceStatusChecker.isEnabled(this)
        tvStatus.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        tvStatus.setTextColor(getColor(if (enabled) R.color.status_ok else R.color.status_bad))
        btnEnable.visibility = if (enabled) Button.GONE else Button.VISIBLE
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add app/src/main/java/com/demo/autolock/MainActivity.kt
git commit -m "feat: 设置界面——状态检测、总开关与时长编辑"
```

---

### 任务 7：README 与真机验收清单

**文件：**
- 创建：`README.md`

- [ ] **步骤 1：编写 `README.md`**

内容必须包含以下章节（完整文案见附录 A）：
1. 功能简介（一句话 + 截图占位不需要）
2. 从 GitHub Actions 下载 APK 的步骤
3. 安装与开启无障碍的步骤
4. ColorOS 保活指引（同 strings.xml guide_body）
5. 真机验收清单（规格第 9 节的 6 条）
6. 技术原理简述（无障碍 TYPE_TOUCH_INTERACTION_START + GLOBAL_ACTION_LOCK_SCREEN + su 兜底）

- [ ] **步骤 2：Commit**

```bash
git add README.md
git commit -m "docs: 使用说明与真机验收清单"
```

---

### 任务 8：最终自检与交付指引

- [ ] **步骤 1：规格覆盖度核对**

对照规格逐节确认：§3 双通道锁屏（任务5）、§4 四组件（任务4/5/6）、§5 边界规则
（任务5 的 screenOff/on 分支 + 任务4 校验 + 任务6 onResume 检测）、§6 UI（任务3/6）、
§7 技术栈（任务1）、§8 CI（任务1/2）、§9 验收清单（任务7）。

- [ ] **步骤 2：交叉一致性检查**

- `R.id.*` 引用与 activity_main.xml 中 id 一一对应
- strings.xml 中 `toast_saved` 的 `%1$s` 与 MainActivity 格式化参数匹配
- Manifest 服务名 `.AutoLockService` 与包名 `com.demo.autolock` 组合正确

- [ ] **步骤 3：全量提交历史检查**

```bash
git log --oneline
git status   # 必须干净
```

- [ ] **步骤 4：输出交付指引给用户**

告知用户：创建 GitHub 私有/公开仓库 → push → 等 Actions 绿勾 → 下载 artifact 安装 →
按 README 开启无障碍与保活 → 跑验收清单。

---

## 附录 A：README.md 完整文案

```markdown
# 自动锁屏

设定 N 分钟内没有任何屏幕触摸操作就自动锁屏熄屏——即使前台是保持常亮的游戏。
适用于 ColorOS 16（Android 9 及以上均可），已 root 设备额外获得 su 兜底锁屏。

## 下载安装

1. 将本仓库推送到 GitHub
2. 打开仓库 Actions 页 → 选择最新一次成功构建 → Artifacts 下载 `autolock-debug-apk`
3. 传输到手机安装（需允许安装未知来源应用）
4. 推送 `v1.0.0` 这类 tag 会自动创建 Release 并附带 APK

## 开启步骤

1. 打开「自动锁屏」App
2. 点击「去开启」，在系统无障碍列表中找到「自动锁屏」并开启
3. 回到 App，状态卡片变绿即生效
4. 输入超时分钟数（1–720），点保存

## ColorOS 保活指引

1. 设置 → 电池 → 更多设置 → 找到「自动锁屏」→ 允许后台高耗电
2. 最近任务卡片下拉 → 锁定「自动锁屏」
3. 若无障碍开关被系统自动关闭，请回到 App 重新开启
4. 手机已 root 时锁屏失败会自动尝试 su 命令兜底

## 真机验收清单

- [ ] 开启无障碍后状态卡片变绿
- [ ] 设 1 分钟，静置桌面 1 分钟后自动熄屏
- [ ] 游戏内（保持亮屏场景）静置同样触发
- [ ] 触摸后倒计时重置（59 秒时触摸不会在 1 秒后熄屏）
- [ ] 关闭总开关后不再触发
- [ ] 熄屏后再亮屏不会被立即锁定

## 技术原理

- 通过无障碍服务订阅全局 `TYPE_TOUCH_INTERACTION_START` 事件感知每一次触摸
  （`canRetrieveWindowContent=false`，不读取任何屏幕内容）
- 每 5 秒轮询一次：无触摸时长 ≥ 设定值 → `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` 锁屏
- 该动作失败时自动回退 `su -c "input keyevent 26"`
- 熄屏期间暂停计时，亮屏重置起点，避免误触发
```
