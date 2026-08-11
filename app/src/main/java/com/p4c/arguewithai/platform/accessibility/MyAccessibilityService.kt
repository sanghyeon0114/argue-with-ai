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

    // Firebase 세션(수동적 사용의 시작~종료) 저장용 상태
    private var firebaseSessionId: SessionId? = null
    private var firebaseSessionStarting: Boolean = false

    // 세션 동안의 화면 방문 기록(재방문도 별도 세그먼트로) 트래킹용 상태
    private var trackedScreenName: String? = null
    private var trackedScreenStartMs: Long = 0L
    private var trackedScreenLastMs: Long = 0L
    private var trackedScreenDurationMs: Long = 0L
    private val screenVisits = mutableListOf<ScreenUsageSummary>()

    private val debugOverlay by lazy { ResultDebugOverlay(this) }
    private val sessionRepository by lazy { FirestoreSessionRepository() }
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Logger.d("🔒 Screen OFF")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Logger.d("🔓 Screen ON")
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

        Logger.d("$result")
        handleFirebaseSession(result, nowMs)
        intervention(result)
        debugOverlay.show(result, hasIntervened)
    }

    // isInvervention(수동적 사용) 상태가 시작/종료될 때 Firestore에 세션(시작~종료 시간)과
    // 세션 동안 머문 화면별 체류시간/시작·종료 시각을 별도 문서로 기록한다.
    private fun handleFirebaseSession(result: PassiveDetectionResult?, nowMs: Long) {
        val isIntervention = result != null && result.isInvervention

        if (isIntervention) {
            trackScreen(result!!, nowMs)

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
        } else {
            val endingSessionId = firebaseSessionId
            if (endingSessionId != null) {
                firebaseSessionId = null
                flushTrackedScreen()
                val screensSnapshot = buildScreenUsageSummaries()

                serviceScope.launch {
                    runCatching { sessionRepository.endSession(endingSessionId) }
                        .onSuccess { ended ->
                            Logger.d("🔥 Firebase session ended: ${endingSessionId.value} (${ended.durationSec}s)")
                            // 1초 미만 세션은 endSession에서 문서가 삭제되므로 화면 기록도 남기지 않는다.
                            if ((ended.durationSec ?: 0) >= 1 && screensSnapshot.isNotEmpty()) {
                                runCatching { sessionRepository.saveScreenUsage(endingSessionId, screensSnapshot) }
                                    .onFailure { Logger.e("🔥 Firebase saveScreenUsage failed", it) }
                            }
                        }
                        .onFailure { Logger.e("🔥 Firebase endSession failed", it) }
                }
            }
        }
    }

    // 화면이 바뀔 때마다 이전 화면의 체류시간/종료 시각을 누적시킨다.
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
        if (trackedScreenDurationMs > 0) {
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
        val openSessionId = firebaseSessionId
        firebaseSessionId = null
        flushTrackedScreen()
        val screensSnapshot = buildScreenUsageSummaries()
        serviceScope.launch {
            sessionId = null
            if (openSessionId != null) {
                runCatching { sessionRepository.endSession(openSessionId) }
                    .onSuccess { ended ->
                        if ((ended.durationSec ?: 0) >= 1 && screensSnapshot.isNotEmpty()) {
                            runCatching { sessionRepository.saveScreenUsage(openSessionId, screensSnapshot) }
                                .onFailure { Logger.e("🔥 Firebase saveScreenUsage failed", it) }
                        }
                    }
                    .onFailure { Logger.e("🔥 Firebase endSession failed", it) }
            }
        }
        serviceScope.cancel()
    }
}