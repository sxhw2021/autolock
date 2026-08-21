package com.demo.autolock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class AutoLockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastTouchTime = 0L
    private var receiverRegistered = false
    @Volatile private var rootMonitorStarted = false
    private var rootProcess: Process? = null
    private var rootMonitorThread: Thread? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkAndLock()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(pollRunnable)
                Intent.ACTION_SCREEN_ON -> {
                    lastTouchTime = SystemClock.elapsedRealtime()
                    startPolling()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastTouchTime = SystemClock.elapsedRealtime()
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        startPolling()
        startRootTouchMonitor()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
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
        if (!prefs.enabled) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val elapsed = SystemClock.elapsedRealtime() - lastTouchTime
        if (elapsed >= prefs.timeoutMinutes * 60_000L) {
            Log.i(TAG, "无操作 ${elapsed / 1000}s ≥ ${prefs.timeoutMinutes} 分钟，执行锁屏")
            lockScreen()
        }
    }

    private fun lockScreen(): Boolean {
        val ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (!ok) {
            Log.w(TAG, "performGlobalAction 失败，尝试 root 兜底")
            lockViaRoot()
        }
        return ok
    }

    private fun lockViaRoot() {
        Thread {
            try {
                val process = ProcessBuilder("su", "-c", "input keyevent 26").start()
                val success = process.waitFor() == 0
                process.destroy()
                Log.i(TAG, "root 兜底锁屏${if (success) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.w(TAG, "root 锁屏失败", e)
            }
        }.start()
    }

    private fun startRootTouchMonitor() {
        if (rootMonitorStarted) return
        rootMonitorStarted = true
        rootMonitorThread = Thread {
            try {
                val process = ProcessBuilder("su", "-c", "getevent -ql").start()
                rootProcess = process
                val reader = process.inputStream.bufferedReader()
                Log.i(TAG, "root 触摸监控已启动")
                while (!Thread.currentThread().isInterrupted) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) lastTouchTime = SystemClock.elapsedRealtime()
                }
                Log.w(TAG, "root 触摸监控流结束")
            } catch (e: Exception) {
                Log.w(TAG, "root 触摸监控不可用，仅依赖无障碍事件", e)
            }
        }.apply { isDaemon = true; start() }
    }

    private fun stopRootTouchMonitor() {
        try { rootProcess?.destroy() } catch (_: Exception) {}
        rootProcess = null
        rootMonitorThread?.interrupt()
        rootMonitorThread = null
        rootMonitorStarted = false
    }

    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        stopRootTouchMonitor()
    }

    companion object {
        private const val TAG = "AutoLockService"
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
