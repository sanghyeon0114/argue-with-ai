package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.intervention.listener.SMListener
import com.p4c.arguewithai.intervention.listener.instagram.PassiveDetectionResult
import com.p4c.arguewithai.intervention.prompt.Prompt
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
import java.util.UUID

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {
    private var interventionEnabled: Boolean = true
    private lateinit var prefs: SharedPreferences
    private var debugOverlayEnabled: Boolean = false
    private val debugOverlay by lazy { ScreenTimeOverlay(applicationContext) }
    private val prompt by lazy { Prompt(applicationContext) }

    companion object {
        private const val PASSIVE_THRESHOLD_MS = 5 * 1000L
        private const val NON_PASSIVE_DEBOUNCE_MS = 500L
    }

    private var hasIntervened: Boolean = false
    private var sessionId: SessionId? = null
    private var trackedPassiveSinceMs: Long? = null
    private var nonPassiveSinceMs: Long? = null

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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val smListener = SMListener()

    override fun onServiceConnected() {
        super.onServiceConnected()
        //Logger.d("[AccessibilityService] 연결됨")
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
        if (root == null) {
            return
        }

        val nowMs = time.nowMs()
        val result = smListener.onEvent(event, root) ?: return

        //Logger.d("$result")
        if (debugOverlayEnabled) {
            displayDebugOverlay(result, nowMs)
        }

        if (!result.isPassive) {
            val since = nonPassiveSinceMs ?: nowMs.also { nonPassiveSinceMs = it }
            val nonPassiveElapsedMs = nowMs - since

            if (nonPassiveElapsedMs >= NON_PASSIVE_DEBOUNCE_MS) {
                hasIntervened = false
                sessionId = null
                trackedPassiveSinceMs = null
            }
            return
        }

        nonPassiveSinceMs = null

        if (trackedPassiveSinceMs != result.passiveSinceMs) {
            trackedPassiveSinceMs = result.passiveSinceMs
            sessionId = SessionId(UUID.randomUUID().toString())
        }

        val passiveElapsedMs = nowMs - result.passiveSinceMs
        if (!hasIntervened && passiveElapsedMs >= PASSIVE_THRESHOLD_MS) {
            val intervened = prompt.show(sessionId)
            hasIntervened = intervened
        }
    }

    private fun displayDebugOverlay(result: PassiveDetectionResult, nowMs: Long) {
        debugOverlay.update(
            screenLabel = result.screen.name,
            screenElapsedMs = nowMs - result.screenSinceMs,
            appLabel = result.app.name,
            appElapsedMs = nowMs - result.passiveSinceMs,
            hasIntervened = hasIntervened
        )
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