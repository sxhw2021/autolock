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
