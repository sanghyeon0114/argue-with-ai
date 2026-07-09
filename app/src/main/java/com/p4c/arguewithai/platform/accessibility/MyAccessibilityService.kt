package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.intervention.listener.SMListener
import com.p4c.arguewithai.platform.overlay.ScreenTimeOverlay
import com.p4c.arguewithai.repository.SessionId
import com.p4c.arguewithai.utils.Logger
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {
    private var interventionEnabled: Boolean = true
    private lateinit var prefs: SharedPreferences
    private var debugOverlayEnabled: Boolean = false
    private val debugOverlay by lazy { ScreenTimeOverlay(applicationContext) }
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "intervention_enabled" -> {
                    interventionEnabled = InterventionPrefs.isEnabled(this)
                    Logger.d("🟢 Intervention enabled = $interventionEnabled")
                }
                "debug_overlay_enabled" -> {
                    val enabled = sp.getBoolean("debug_overlay_enabled", false)
                    debugOverlayEnabled = enabled
                    if (enabled) debugOverlay.start() else debugOverlay.stop()
                    Logger.d("🟣 Debug overlay enabled = $enabled")
                }
            }
        }
    private var sessionId: SessionId? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val smListener = SMListener()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("[AccessibilityService] 연결됨")

        prefs = getSharedPreferences("argue_prefs", MODE_PRIVATE).also {
            interventionEnabled = it.getBoolean("intervention_enabled", true)
            debugOverlayEnabled = it.getBoolean("debug_overlay_enabled", false)
            it.registerOnSharedPreferenceChangeListener(prefListener)
        }
        if (debugOverlayEnabled) {
            debugOverlay.start()
        }

        FirebaseApp.initializeApp(this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener { Logger.d("Firebase login ok") }
                .addOnFailureListener { Logger.e("Firebase login fail", it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow

        smListener.onEvent(event, root) { label, screenElapsedMs ->
            debugOverlay.update(label, screenElapsedMs)
        }
    }

    override fun onInterrupt() {
        // pass
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        serviceScope.launch { sessionId = null }
        serviceScope.cancel()
    }
}
