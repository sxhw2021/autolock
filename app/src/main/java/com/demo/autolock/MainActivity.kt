package com.demo.autolock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsRepository(this)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val swEnabled = findViewById<SwitchMaterial>(R.id.swEnabled)
        val etTimeout = findViewById<EditText>(R.id.etTimeout)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val cardStatus = findViewById<MaterialCardView>(R.id.cardStatus)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        swEnabled.isChecked = prefs.enabled
        swEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
        }

        etTimeout.setText(prefs.timeoutMinutes.toString())
        btnSave.setOnClickListener {
            val parsed = TimeoutValidator.parse(etTimeout.text.toString())
            if (parsed == null) {
                Toast.makeText(this, getString(R.string.toast_invalid), Toast.LENGTH_SHORT).show()
                etTimeout.setText(prefs.timeoutMinutes.toString())
            } else {
                prefs.timeoutMinutes = parsed
                Toast.makeText(
                    this, getString(R.string.toast_saved, parsed.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        cardStatus.setOnClickListener { refreshStatus(tvStatus, btnEnable) }
        refreshStatus(tvStatus, btnEnable)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(findViewById(R.id.tvStatus), findViewById(R.id.btnEnable))
    }

    private fun refreshStatus(tvStatus: TextView, btnEnable: Button) {
        val enabled = ServiceStatusChecker.isEnabled(this)
        tvStatus.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled)
        tvStatus.setTextColor(getColor(if (enabled) R.color.status_ok else R.color.status_bad))
        btnEnable.visibility = if (enabled) Button.GONE else Button.VISIBLE
    }
}
