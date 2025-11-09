package com.example.arguewithai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.arguewithai.firebase.FirestoreAccessibilityRepository
import com.example.arguewithai.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.edit
import com.example.arguewithai.chat.ChatActivity

class MainActivity : ComponentActivity() {
    private lateinit var accessibilityText: TextView

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val accKey = "last_accessibility_enabled"
    private val accessRepo = FirestoreAccessibilityRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setView()

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
        accessibilityText.text = serviceStatusText()

        val enabled = isMyAccessibilityServiceEnabled()
        val last = prefs.getBoolean(accKey, false)
        if (last != enabled) {
            accessRepo.setAccessibilityEnabled(enabled) { ok ->
                if (ok) prefs.edit {
                    putBoolean(accKey, enabled)
                }
            }
        }
    }

    private fun setView() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        fun divider(): View = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2  // 선 두께 (dp 적용 아래서 해줌)
            ).apply {
                topMargin = 32
                bottomMargin = 32
            }
            setBackgroundColor(getColor(android.R.color.darker_gray))
        }

        val overlayInfoText = TextView(this).apply {
            text = "오버레이 권한을 허용해주세요."
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val accessibilityInfoText = TextView(this).apply {
            text = "숏폼 사용 감지를 위해 접근성 서비스 활성화가 필요합니다."
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        accessibilityText = TextView(this).apply {
            text = serviceStatusText() // ex: "접근성 서비스: OFF"
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val overlayBtn = Button(this).apply {
            text = "오버레이 권한 설정"
            setOnClickListener { requestOverlayPermission() }
        }

        val accessibilityBtn = Button(this).apply {
            text = "접근성 서비스 설정"
            setOnClickListener { openAccessibilitySettingsCompat() }
        }

        layout.addView(overlayInfoText)
        layout.addView(overlayBtn)
        layout.addView(divider())
        layout.addView(accessibilityInfoText)
        layout.addView(accessibilityText)
        layout.addView(accessibilityBtn)

        setContentView(layout)
    }

    private fun serviceStatusText(): String {
        return if (isMyAccessibilityServiceEnabled()) {
            "상태: 활성화됨 ✅"
        } else {
            "상태: 비활성화됨 ❌"
        }
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

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "이미 오버레이 권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
