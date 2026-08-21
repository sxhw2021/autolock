package com.demo.autolock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
