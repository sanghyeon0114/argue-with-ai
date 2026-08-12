package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.app.DebugOverlayPrefs
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.intervention.listener.SMListener
import com.p4c.arguewithai.intervention.prompt.Prompt
import com.p4c.arguewithai.repository.FirestoreSessionRepository
import com.p4c.arguewithai.repository.ScreenUsageSummary
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
        private const val PASSIVE_THRESHOLD_MS = 30  * 1000L
        private const val NON_PASSIVE_RESET_STREAK = 20
    }

    private var hasIntervened: Boolean = false
    private var sessionId: SessionId? = null
    private var wasPassive: Boolean = false
    private var nonPassiveHitStreak: Int = 0
    private var isScreenOn: Boolean = true
    private var screenReceiverRegistered: Boolean = false
    private var debugOverlayEnabled: Boolean = true

    private var firebaseSessionId: SessionId? = null
    private var firebaseSessionStarting: Boolean = false

    private var trackedScreenName: String? = null
    private var trackedScreenStartMs: Long = 0L
    private var trackedScreenLastMs: Long = 0L
    private var trackedScreenDurationMs: Long = 0L
    private val screenVisits = mutableListOf<ScreenUsageSummary>()

    private val debugOverlay by lazy { ResultDebugOverlay(this) }
    private val sessionRepository by lazy { FirestoreSessionRepository() }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    smListener.resetPassive(time.nowMs())
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                }
            }
        }
    }
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "intervention_enabled" -> {
                    interventionEnabled = InterventionPrefs.isEnabled(this)
                    Logger.d("🟢 Intervention enabled = $interventionEnabled")
                }
                DebugOverlayPrefs.KEY -> {
                    debugOverlayEnabled = DebugOverlayPrefs.isEnabled(this)
                    if (!debugOverlayEnabled) debugOverlay.hide()
                    Logger.d("🟢 Debug overlay enabled = $debugOverlayEnabled")
                }
            }
        }
    private val smListener = SMListener()

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("argue_prefs", MODE_PRIVATE).also {
            interventionEnabled = it.getBoolean("intervention_enabled", true)
            it.registerOnSharedPreferenceChangeListener(prefListener)
        }
        debugOverlayEnabled = DebugOverlayPrefs.isEnabled(this)

        FirebaseApp.initializeApp(this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener { Logger.d("Firebase login ok") }
                .addOnFailureListener { Logger.e("Firebase login fail", it) }
        }

        if (!screenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
            screenReceiverRegistered = true
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
            .takeIf { isScreenOn }

        //Logger.d("$result")
        checkUsage(result, nowMs)
        intervention(result)
        if (debugOverlayEnabled) {
            debugOverlay.show(result, hasIntervened)
        } else {
            debugOverlay.hide()
        }
    }

    private fun checkUsage(result: PassiveDetectionResult?, nowMs: Long) {
        if (result == null) {
            endFirebaseSession()
            return
        }

        trackScreen(result, nowMs)

        if (firebaseSessionId == null && !firebaseSessionStarting) {
            firebaseSessionStarting = true
            val app = result.app.label
            serviceScope.launch {
                runCatching { sessionRepository.startSession(app) }
                    .onSuccess {
                        firebaseSessionId = it
                        Logger.d("🔥 Firebase session started: ${it.value}")
                    }
                    .onFailure { Logger.e("🔥 Firebase startSession failed", it) }
                firebaseSessionStarting = false
            }
        }
    }

    private fun endFirebaseSession() {
        val endingSessionId = firebaseSessionId ?: return
        firebaseSessionId = null
        flushTrackedScreen()
        val screensSnapshot = buildScreenUsageSummaries()

        serviceScope.launch {
            runCatching { sessionRepository.endSession(endingSessionId) }
                .onSuccess { ended ->
                    Logger.d("🔥 Firebase session ended: ${endingSessionId.value} (${ended.durationSec}s)")
                    if (screensSnapshot.isNotEmpty()) {
                        runCatching { sessionRepository.saveScreenUsage(endingSessionId, screensSnapshot) }
                            .onFailure { Logger.e("🔥 Firebase saveScreenUsage failed", it) }
                    }
                }
                .onFailure { Logger.e("🔥 Firebase endSession failed", it) }
        }
    }

    private fun trackScreen(result: PassiveDetectionResult, nowMs: Long) {
        val screenName = result.screen?.toString() ?: return
        if (screenName != trackedScreenName) {
            flushTrackedScreen()
            trackedScreenName = screenName
            trackedScreenStartMs = nowMs - result.screenMs
        }
        trackedScreenDurationMs = result.screenMs
        trackedScreenLastMs = nowMs
    }

    private fun flushTrackedScreen() {
        val screen = trackedScreenName ?: return
        if (screen != "NONE" && trackedScreenDurationMs > 0) {
            screenVisits.add(
                ScreenUsageSummary(
                    screen = screen,
                    durationMs = trackedScreenDurationMs,
                    startEpochMs = trackedScreenStartMs,
                    endEpochMs = trackedScreenLastMs
                )
            )
        }
        trackedScreenName = null
        trackedScreenStartMs = 0L
        trackedScreenDurationMs = 0L
        trackedScreenLastMs = 0L
    }

    private fun buildScreenUsageSummaries(): List<ScreenUsageSummary> {
        val summaries = screenVisits.toList()
        screenVisits.clear()
        return summaries
    }

    private fun intervention(result: PassiveDetectionResult?) {
        val isIntervention = result != null && (result.isInvervention)

        if (!isIntervention) {
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
        debugOverlay.hide()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }
        endFirebaseSession()
        sessionId = null
        serviceScope.cancel()
    }
}