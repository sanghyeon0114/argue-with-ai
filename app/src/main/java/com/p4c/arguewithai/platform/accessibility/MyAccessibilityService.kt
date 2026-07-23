package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.intervention.listener.SMListener
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.prompt.Prompt
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
    private val prompt by lazy { Prompt(applicationContext) }

    companion object {
        private const val PASSIVE_THRESHOLD_MS = 5 * 1000L
        private const val NON_PASSIVE_RESET_STREAK = 10
    }

    private var hasIntervened: Boolean = false
    private var sessionId: SessionId? = null
    private var wasPassive: Boolean = false
    private var nonPassiveHitStreak: Int = 0

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "intervention_enabled" -> {
                    interventionEnabled = InterventionPrefs.isEnabled(this)
                    Logger.d("🟢 Intervention enabled = $interventionEnabled")
                }
            }
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val smListener = SMListener()

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("argue_prefs", MODE_PRIVATE).also {
            interventionEnabled = it.getBoolean("intervention_enabled", true)
            it.registerOnSharedPreferenceChangeListener(prefListener)
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

        val nowMs: Long = time.nowMs()
        val result: PassiveDetectionResult? = smListener.onEvent(event, root, nowMs)

        //Logger.d("$result")
        intervention(result)
    }

    private fun intervention(result: PassiveDetectionResult?) {
        val isPassive = result != null && (result.isPassive || result.app == SocialMediaApp.INTERVENTION)

        if (!isPassive) {
            wasPassive = false
            nonPassiveHitStreak++

            if (nonPassiveHitStreak >= NON_PASSIVE_RESET_STREAK) {
                setHasIntervened(false)
                sessionId = null
            }
            return
        }

        nonPassiveHitStreak = 0

        if (!wasPassive) {
            wasPassive = true
            sessionId = SessionId(UUID.randomUUID().toString())
        }

        if (!hasIntervened && result!!.passiveMs >= PASSIVE_THRESHOLD_MS) {
            val intervened = prompt.show(sessionId)
            setHasIntervened(intervened)
        }
    }

    private fun setHasIntervened(value: Boolean) {
        if (hasIntervened == value) return
        hasIntervened = value
        Logger.d("🔔 hasIntervened = $hasIntervened")
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