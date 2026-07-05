package com.p4c.arguewithai.app

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.p4c.arguewithai.R

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("argue_prefs", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val btnOverlay = findViewById<Button>(R.id.btnOverlay)

        var overlayEnabled = prefs.getBoolean("debug_overlay_enabled", false)
        updateOverlayButtonText(btnOverlay, overlayEnabled)

        btnOverlay.setOnClickListener {
            overlayEnabled = !overlayEnabled
            prefs.edit().putBoolean("debug_overlay_enabled", overlayEnabled).apply()
            updateOverlayButtonText(btnOverlay, overlayEnabled)
        }
    }

    private fun updateOverlayButtonText(button: Button, enabled: Boolean) {
        button.text = if (enabled) "ON" else "OFF"
    }
}