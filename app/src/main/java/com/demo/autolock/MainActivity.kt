package com.demo.autolock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — FGS still runs, notification just hidden */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsRepository(this)

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val btnBattery = findViewById<Button>(R.id.btnBattery)
        val swEnabled = findViewById<SwitchMaterial>(R.id.swEnabled)
        val etTimeout = findViewById<EditText>(R.id.etTimeout)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val cardStatus = findViewById<MaterialCardView>(R.id.cardStatus)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnBattery.setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        swEnabled.isChecked = prefs.enabled
        swEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            if (checked) KeepAliveService.start(this) else KeepAliveService.stop(this)
        }

        etTimeout.setText(prefs.timeoutMinutes.toString())
        btnSave.setOnClickListener {
            val parsed = TimeoutValidator.parse(etTimeout.text.toString())
            if (parsed == null) {
                Toast.makeText(this, getString(R.string.toast_invalid), Toast.LENGTH_SHORT).show()
                etTimeout.setText(prefs.timeoutMinutes.toString())
            } else {
                prefs.timeoutMinutes = parsed
                if (prefs.enabled) KeepAliveService.start(this)
                Toast.makeText(
                    this, getString(R.string.toast_saved, parsed.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        cardStatus.setOnClickListener { refreshStatus(tvStatus, btnEnable, btnBattery) }
        refreshStatus(tvStatus, btnEnable, btnBattery)

        if (prefs.enabled) KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(findViewById(R.id.tvStatus), findViewById(R.id.btnEnable), findViewById(R.id.btnBattery))
    }

    private fun refreshStatus(tvStatus: TextView, btnEnable: Button, btnBattery: Button) {
        val enabled = ServiceStatusChecker.isEnabled(this)
        tvStatus.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        tvStatus.setTextColor(getColor(if (enabled) R.color.status_ok else R.color.status_bad))
        btnEnable.visibility = if (enabled) Button.GONE else Button.VISIBLE
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        btnBattery.visibility = if (pm.isIgnoringBatteryOptimizations(packageName)) Button.GONE else Button.VISIBLE
    }
}
