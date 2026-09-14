package com.demo.autolock

import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import java.io.File
import java.lang.reflect.Executable

class AutoLockModule : XposedModule() {

    @Volatile private var lastActivityTime = 0L
    @Volatile private var timeoutMs = DEFAULT_TIMEOUT * 60_000L
    @Volatile private var enabled = true

    private var serviceInstance: Any? = null
    private var lastPrefsLoad = 0L
    private var timerThread: Thread? = null

    private val userActivityHooker = Hooker { chain ->
        serviceInstance = chain.getThisObject()
        onUserActivity()
        chain.proceed()
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "模块加载: ${param.processName}")
        loadSettings()
        hookUserActivity()
    }

    private fun loadSettings() {
        // 尝试读取 App 写入的设置文件
        try {
            val file = File("/data/data/$PKG_MODULE/files/$SETTINGS_FILE")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                val enabledMatch = Regex(""""enabled"\s*:\s*(true|false)""").find(content)
                val timeoutMatch = Regex(""""timeout_minutes"\s*:\s*(\d+)""").find(content)
                enabledMatch?.let { enabled = it.groupValues[1].toBoolean() }
                timeoutMatch?.let { timeoutMs = it.groupValues[1].toInt() * 60_000L }
                log(Log.INFO, TAG, "设置读取成功: enabled=$enabled, timeout=${timeoutMs / 1000}s")
                return
            }
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "设置读取失败: ${e.message}")
        }
        log(Log.INFO, TAG, "使用默认设置: enabled=$enabled, timeout=${timeoutMs / 1000}s")
    }

    private fun hookUserActivity() {
        try {
            val clazz = Class.forName(CLASS_POWER_MANAGER_SERVICE)
            val method = clazz.getDeclaredMethod(
                METHOD_USER_ACTIVITY,
                Long::class.java,
                Int::class.java,
                Int::class.java
            )
            hook(method).setId("userActivity").intercept(userActivityHooker)
            log(Log.INFO, TAG, "Hook 成功: $CLASS_POWER_MANAGER_SERVICE.$METHOD_USER_ACTIVITY")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "Hook 失败: ${e.message}")
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
            log(Log.INFO, TAG, "无操作 ${elapsed / 1000}s >= ${timeoutMs / 1000}s，锁屏")
            goToSleep()
        }
    }

    private fun goToSleep() {
        val svc = serviceInstance ?: return
        try {
            val method = svc.javaClass.getDeclaredMethod(
                "goToSleep", Long::class.java, Int::class.java, Int::class.java
            )
            method.isAccessible = true
            method.invoke(svc, SystemClock.uptimeMillis(), 0, 0)
            log(Log.INFO, TAG, "goToSleep 成功")
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "goToSleep 失败: ${e.message}，尝试 su")
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26")).waitFor()
                log(Log.INFO, TAG, "su 锁屏成功")
            } catch (e2: Throwable) {
                log(Log.WARN, TAG, "su 锁屏失败: ${e2.message}")
            }
        }
    }

    private fun findClass(name: String): Class<*>? = try {
        Class.forName(name, false, javaClass.classLoader)
    } catch (e: Throwable) {
        log(Log.WARN, TAG, "$name not found")
        null
    }

    companion object {
        private const val TAG = "AutoLock"
        private const val PKG_MODULE = "com.demo.autolock"
        private const val CLASS_POWER_MANAGER_SERVICE =
            "com.android.server.power.PowerManagerService"
        private const val METHOD_USER_ACTIVITY = "userActivity"
        private const val SETTINGS_FILE = "settings.json"
        private const val DEFAULT_TIMEOUT = 5
        private const val PREFS_INTERVAL = 5000L
    }
}
