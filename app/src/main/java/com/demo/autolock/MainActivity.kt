package com.demo.autolock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val swEnabled = findViewById<Switch>(R.id.swEnabled)
        val etTimeout = findViewById<EditText>(R.id.etTimeout)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val timeout = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT)

        swEnabled.isChecked = enabled
        etTimeout.setText(timeout.toString())
        refreshStatus(tvStatus, enabled)

        swEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
            refreshStatus(tvStatus, isChecked)
        }

        btnSave.setOnClickListener {
            val input = etTimeout.text.toString().trim()
            val minutes = input.toIntOrNull()
            if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
                Toast.makeText(this, getString(R.string.toast_invalid), Toast.LENGTH_SHORT).show()
                etTimeout.setText(prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT).toString())
            } else {
                prefs.edit().putInt(KEY_TIMEOUT, minutes).apply()
                Toast.makeText(this, getString(R.string.toast_saved, minutes.toString()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshStatus(tvStatus: TextView, enabled: Boolean) {
        tvStatus.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        tvStatus.setTextColor(if (enabled) 0xFF1B873B.toInt() else 0xFFC62828.toInt())
    }

    companion object {
        private const val PREFS_FILE = "settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT = "timeout_minutes"
        private const val DEFAULT_TIMEOUT = 5
        private const val MIN_MINUTES = 1
        private const val MAX_MINUTES = 720
    }
}
