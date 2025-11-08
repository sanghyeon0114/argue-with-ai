package com.example.arguewithai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.arguewithai.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private lateinit var accessibilityServiceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val infoText = TextView(this).apply {
            text = getString(R.string.accessibility_guide)
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        accessibilityServiceText = TextView(this).apply {
            text = serviceStatusText()
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }


        val btn = Button(this).apply {
            text = "접근성 설정"
            setOnClickListener {
                openAccessibilitySettingsCompat()
            }
        }

        layout.addView(infoText)
        layout.addView(accessibilityServiceText)
        layout.addView(btn)

        setContentView(layout)

        Logger.enabled = true
        FirebaseApp.initializeApp(this)

        val ctx = applicationContext
        Logger.d("pkg = ${ctx.packageName}")

        try {
            val opts = FirebaseApp.getInstance().options
            Logger.d("projectId = ${opts.projectId}")
            Logger.d("appId     = ${opts.applicationId}")
            Logger.d("apiKey    = ${opts.apiKey}")
        } catch (e: Exception) {
            Logger.e("FirebaseApp.getInstance() FAILED", e)
        }

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener { Logger.d("✅[Firebase] Logged in: ${it.user?.uid}") }
            .addOnFailureListener { Logger.e("❌[Firebase] Login failed", it) }
    }

    override fun onResume() {
        super.onResume()
        accessibilityServiceText.text = serviceStatusText()
    }

    private fun serviceStatusText(): String {
        return "AccessibilityService: ${isMyAccessibilityServiceEnabled()}"
    }

    private fun isMyAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ComponentName(this, MyAccessibilityService::class.java)
        return enabled.any {
            val s = it.resolveInfo.serviceInfo
            s.packageName == target.packageName && s.name == target.className
        }
    }

    private fun openAccessibilitySettingsCompat() {
        val generalIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        when {
            tryStart(generalIntent) -> Unit
            else -> {
                Toast.makeText(this, R.string.accessibility_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tryStart(intent: Intent): Boolean {
        return try {
            val canHandle = intent.resolveActivity(packageManager) != null
            if (!canHandle) return false
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
