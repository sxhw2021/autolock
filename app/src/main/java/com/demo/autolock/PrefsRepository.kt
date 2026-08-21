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
