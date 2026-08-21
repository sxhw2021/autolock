package com.demo.autolock

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedHook : IXposedHookLoadPackage {

    private val handler = Handler(Looper.getMainLooper())
    private var lastActivityTime = 0L
    private var timeoutMs = 5 * 60_000L
    private var enabled = true
    private var prefs: XSharedPreferences? = null
    private var lastSettingsLoad = 0L
    private var serviceInstance: Any? = null

    private val lockRunnable = Runnable { checkAndLock() }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != ANDROID_PACKAGE) return

        loadPrefs()
        loadSettings()

        try {
            XposedHelpers.findAndHookMethod(
                POWER_MANAGER_SERVICE,
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
            Log.i(TAG, "Hook 成功: $POWER_MANAGER_SERVICE.$METHOD_USER_ACTIVITY")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook 失败", e)
        }
    }

    private fun loadPrefs() {
        try {
            prefs = XSharedPreferences(MODULE_PACKAGE, PREFS_FILE)
            prefs?.makeWorldReadable()
        } catch (e: Throwable) {
            Log.w(TAG, "XSharedPreferences 加载失败", e)
        }
    }

    private fun loadSettings() {
        prefs?.let {
            it.reload()
            enabled = it.getBoolean(KEY_ENABLED, true)
            timeoutMs = it.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT) * 60_000L
        }
    }

    private fun onUserActivity() {
        val now = System.currentTimeMillis()
        if (now - lastSettingsLoad > SETTINGS_RELOAD_INTERVAL_MS) {
            lastSettingsLoad = now
            loadSettings()
        }
        if (!enabled) return

        lastActivityTime = SystemClock.elapsedRealtime()
        handler.removeCallbacks(lockRunnable)
        handler.postDelayed(lockRunnable, timeoutMs)
    }

    private fun checkAndLock() {
        if (!enabled) return
        val elapsed = SystemClock.elapsedRealtime() - lastActivityTime
        if (elapsed >= timeoutMs) {
            Log.i(TAG, "无操作 ${elapsed / 1000}s >= ${timeoutMs / 1000}s，执行锁屏")
            goToSleep()
        }
    }

    private fun goToSleep() {
        val svc = serviceInstance ?: return
        try {
            XposedHelpers.callMethod(svc, METHOD_GO_TO_SLEEP, SystemClock.uptimeMillis(), 0, 0)
            Log.i(TAG, "goToSleep 成功")
        } catch (e: Throwable) {
            Log.w(TAG, "goToSleep 失败，尝试 root 兜底", e)
            try {
                Runtime.getRuntime().exec(arrayOf(ROOT_CMD, ROOT_ARG_LOCK)).waitFor()
            } catch (e2: Throwable) {
                Log.e(TAG, "root 锁屏也失败", e2)
            }
        }
    }

    companion object {
        private const val TAG = "AutoLock"
        private const val ANDROID_PACKAGE = "android"
        private const val MODULE_PACKAGE = "com.demo.autolock"
        private const val PREFS_FILE = "settings"
        private const val POWER_MANAGER_SERVICE = "com.android.server.power.PowerManagerService"
        private const val METHOD_USER_ACTIVITY = "userActivity"
        private const val METHOD_GO_TO_SLEEP = "goToSleep"
        private const val ROOT_CMD = "su"
        private const val ROOT_ARG_LOCK = "-c"
        private const val ROOT_ARG_LOCK_CMD = "input keyevent 26"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT = "timeout_minutes"
        private const val DEFAULT_TIMEOUT = 5
        private const val SETTINGS_RELOAD_INTERVAL_MS = 5000L
    }
}
