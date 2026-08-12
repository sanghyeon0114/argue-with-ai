package com.p4c.arguewithai.app

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.p4c.arguewithai.R

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val switchDebugOverlay = findViewById<Switch>(R.id.switchDebugOverlay)
        switchDebugOverlay.isChecked = DebugOverlayPrefs.isEnabled(this)
        switchDebugOverlay.setOnCheckedChangeListener { _, isChecked ->
            DebugOverlayPrefs.setEnabled(this, isChecked)
        }
    }
}