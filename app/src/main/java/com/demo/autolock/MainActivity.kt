package com.demo.autolock

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val swEnabled = findViewById<Switch>(R.id.swEnabled)
        val etTimeout = findViewById<EditText>(R.id.etTimeout)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val timeout = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT)

        swEnabled.isChecked = enabled
        etTimeout.setText(timeout.toString())
        updateStatus(tvStatus, enabled)

        swEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
            saveToFile()
            updateStatus(tvStatus, isChecked)
        }

        btnSave.setOnClickListener {
            val minutes = etTimeout.text.toString().trim().toIntOrNull()
            if (minutes == null || minutes < 1 || minutes > 720) {
                Toast.makeText(this, R.string.toast_invalid, Toast.LENGTH_SHORT).show()
                etTimeout.setText(prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT).toString())
            } else {
                prefs.edit().putInt(KEY_TIMEOUT, minutes).apply()
                saveToFile()
                Toast.makeText(this, getString(R.string.toast_saved, minutes), Toast.LENGTH_SHORT).show()
            }
        }

        saveToFile()
    }

    private fun saveToFile() {
        try {
            val json = """{"enabled":${prefs.getBoolean(KEY_ENABLED, true)},"timeout_minutes":${prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT)}}"""
            val file = File(filesDir, SETTINGS_FILE)
            file.writeText(json)
            file.setReadable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateStatus(tv: TextView, enabled: Boolean) {
        tv.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        tv.setTextColor(if (enabled) 0xFF1B873B.toInt() else 0xFFC62828.toInt())
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val SETTINGS_FILE = "settings.json"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT = "timeout_minutes"
        private const val DEFAULT_TIMEOUT = 5
    }
}
