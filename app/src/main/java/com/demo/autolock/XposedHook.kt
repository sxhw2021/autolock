package com.demo.autolock

import android.content.Context
import android.os.SystemClock
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.lang.reflect.Method

class XposedHook : IXposedHookLoadPackage {

    @Volatile private var lastActivityTime = 0L
    @Volatile private var timeoutMs = DEFAULT_TIMEOUT * 60_000L
    @Volatile private var enabled = true

    private var serviceInstance: Any? = null
    private var lastPrefsLoad = 0L
    private var timerThread: Thread? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG_ANDROID) return
        log("模块加载")
        loadSettings()
        hookUserActivity(lpparam)
    }

    private fun loadSettings() {
        // 方法1: 通过 ContentProvider
        try {
            val ctx = getSystemContext()
            if (ctx != null) {
                val cursor = ctx.contentResolver.query(
                    SettingsProvider.CONTENT_URI, null, null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        enabled = it.getString(it.getColumnIndexOrThrow(SettingsProvider.KEY_ENABLED)).toBoolean()
                        timeoutMs = it.getInt(it.getColumnIndexOrThrow(SettingsProvider.KEY_TIMEOUT)) * 60_000L
                        log("设置(ContentProvider): enabled=$enabled, timeout=${timeoutMs / 1000}s")
                        return
                    }
                }
            }
        } catch (e: Throwable) {
            log("ContentProvider 读取失败: ${e.message}")
        }

        // 方法2: 直接读文件
        try {
            val file = File("/data/data/$PKG_MODULE/files/$SETTINGS_FILE")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                parseSettings(content)
                log("设置(文件): enabled=$enabled, timeout=${timeoutMs / 1000}s")
                return
            }
        } catch (e: Throwable) {
            log("文件读取失败: ${e.message}")
        }

        // 方法3: 使用默认值
        log("使用默认设置: enabled=$enabled, timeout=${timeoutMs / 1000}s")
    }

    private fun parseSettings(json: String) {
        try {
            val enabledMatch = Regex(""""enabled"\s*:\s*(true|false)""").find(json)
            val timeoutMatch = Regex(""""timeout_minutes"\s*:\s*(\d+)""").find(json)
            enabledMatch?.let { enabled = it.groupValues[1].toBoolean() }
            timeoutMatch?.let { timeoutMs = it.groupValues[1].toInt() * 60_000L }
        } catch (_: Throwable) {}
    }

    private fun getSystemContext(): Context? {
        return try {
            val at = Class.forName("android.app.ActivityThread")
            val current = at.getMethod("currentActivityThread").invoke(null)
            at.getMethod("getSystemContext").invoke(current) as? Context
        } catch (e: Throwable) {
            log("getSystemContext 失败: ${e.message}")
            null
        }
    }

    private fun hookUserActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_POWER_MANAGER_SERVICE,
                lpparam.classLoader,
                METHOD_USER_ACTIVITY,
                Long::class.java, Int::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        serviceInstance = param.thisObject
                        onUserActivity()
                    }
                }
            )
            log("Hook 成功")
        } catch (e: Throwable) {
            log("Hook 失败: ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun onUserActivity() {
        val now = System.currentTimeMillis()
        if (now - lastPrefsLoad > PREFS_INTERVAL) {
            lastPrefsLoad = now
            loadSettings()
        }
        if (!enabled) return
        lastActivityTime = SystemClock.elapsedRealtime()
        startTimer()
    }

    private fun startTimer() {
        timerThread?.interrupt()
        timerThread = Thread {
            try {
                Thread.sleep(timeoutMs)
                checkAndLock()
            } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; name = "AutoLock-Timer"; start() }
    }

    private fun checkAndLock() {
        if (!enabled) return
        val elapsed = SystemClock.elapsedRealtime() - lastActivityTime
        if (elapsed >= timeoutMs) {
            log("无操作 ${elapsed / 1000}s >= ${timeoutMs / 1000}s，锁屏")
            goToSleep()
        }
    }

    private fun goToSleep() {
        val svc = serviceInstance ?: return
        try {
            val m = svc.javaClass.getDeclaredMethod(
                "goToSleep", Long::class.java, Int::class.java, Int::class.java
            )
            m.isAccessible = true
            m.invoke(svc, SystemClock.uptimeMillis(), 0, 0)
            log("goToSleep 成功")
        } catch (e: Throwable) {
            log("goToSleep 失败: ${e.message}，尝试 su")
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26")).waitFor()
                log("su 锁屏成功")
            } catch (e2: Throwable) {
                log("su 锁屏失败: ${e2.message}")
            }
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("[$TAG] $msg")
    }

    companion object {
        private const val TAG = "AutoLock"
        private const val PKG_ANDROID = "android"
        private const val PKG_MODULE = "com.demo.autolock"
        private const val CLASS_POWER_MANAGER_SERVICE =
            "com.android.server.power.PowerManagerService"
        private const val METHOD_USER_ACTIVITY = "userActivity"
        private const val SETTINGS_FILE = "settings.json"
        private const val DEFAULT_TIMEOUT = 5
        private const val PREFS_INTERVAL = 5000L
    }
}
