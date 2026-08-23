package com.demo.autolock

import android.os.SystemClock
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class XposedHook : IXposedHookLoadPackage {

    @Volatile private var lastActivityTime = 0L
    @Volatile private var timeoutMs = DEFAULT_TIMEOUT_MIN * 60_000L
    @Volatile private var enabled = true

    private var prefs: XSharedPreferences? = null
    private var serviceInstance: Any? = null
    private var goToSleepMethod: Method? = null
    private var lastPrefsLoad = 0L
    private var timerThread: Thread? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG_ANDROID) return
        log("handleLoadPackage: ${lpparam.packageName}")
        initPrefs()
        loadSettings()
        hookUserActivity(lpparam)
    }

    private fun initPrefs() {
        try {
            prefs = XSharedPreferences(PKG_MODULE, PREFS_NAME)
            prefs?.reload()
            log("XSharedPreferences 加载成功")
        } catch (e: Throwable) {
            log("XSharedPreferences 加载失败: ${e.message}")
        }
    }

    private fun loadSettings() {
        prefs?.let {
            it.reload()
            enabled = it.getBoolean(KEY_ENABLED, true)
            timeoutMs = it.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_MIN) * 60_000L
            log("设置: enabled=$enabled, timeout=${timeoutMs / 1000}s")
        }
    }

    private fun hookUserActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_POWER_MANAGER_SERVICE,
                lpparam.classLoader,
                METHOD_USER_ACTIVITY,
                Long::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        serviceInstance = param.thisObject
                        onUserActivity()
                    }
                }
            )
            log("Hook 成功: $CLASS_POWER_MANAGER_SERVICE.$METHOD_USER_ACTIVITY")
        } catch (e: Throwable) {
            log("Hook 失败: ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun onUserActivity() {
        val now = System.currentTimeMillis()
        if (now - lastPrefsLoad > PREFS_RELOAD_INTERVAL) {
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
            } catch (_: InterruptedException) {
            }
        }.apply {
            isDaemon = true
            name = "AutoLock-Timer"
            start()
        }
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
        val svc = serviceInstance
        if (svc == null) {
            log("serviceInstance 为 null，跳过")
            return
        }
        try {
            val method = svc.javaClass.getDeclaredMethod(
                METHOD_GO_TO_SLEEP,
                Long::class.java,
                Int::class.java,
                Int::class.java
            )
            method.isAccessible = true
            method.invoke(svc, SystemClock.uptimeMillis(), 0, 0)
            log("goToSleep 成功")
        } catch (e: Throwable) {
            log("goToSleep 失败: ${e.message}")
            fallbackLock()
        }
    }

    private fun fallbackLock() {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", CMD_LOCK))
            val exit = process.waitFor()
            log("su 锁屏 exit=$exit")
        } catch (e: Throwable) {
            log("su 锁屏失败: ${e.message}")
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("[$TAG] $msg")
    }

    companion object {
        private const val TAG = "AutoLock"
        private const val PKG_ANDROID = "android"
        private const val PKG_MODULE = "com.demo.autolock"
        private const val PREFS_NAME = "settings"
        private const val CLASS_POWER_MANAGER_SERVICE =
            "com.android.server.power.PowerManagerService"
        private const val METHOD_USER_ACTIVITY = "userActivity"
        private const val METHOD_GO_TO_SLEEP = "goToSleep"
        private const val CMD_LOCK = "input keyevent 26"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT = "timeout_minutes"
        private const val DEFAULT_TIMEOUT_MIN = 5
        private const val PREFS_RELOAD_INTERVAL = 5000L
    }
}
