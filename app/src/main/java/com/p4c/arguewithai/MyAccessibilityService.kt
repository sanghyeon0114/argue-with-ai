package com.p4c.arguewithai


import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.accessibility.AccessibilityEvent
import com.p4c.arguewithai.chat.ChatActivity
import com.p4c.arguewithai.firebase.FirestoreSessionRepository
import com.p4c.arguewithai.firebase.SessionId
import com.p4c.arguewithai.firebase.SessionRepository
import com.p4c.arguewithai.utils.Logger
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {
    private var interventionEnabled: Boolean = true
    private lateinit var prefs: SharedPreferences
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "intervention_enabled") {
                interventionEnabled = InterventionPrefs.isEnabled(this)
                Logger.d("🟢 Intervention enabled = $interventionEnabled")
            }
        }
    private val repo: SessionRepository = FirestoreSessionRepository()
    private var sessionId: SessionId? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sessionMutex = Mutex()
    private var lastChatAt: Long = 0L
    private val cooltimeMs: Long = 5 * 1000L //10 * 60 * 1000L
    @Volatile private var isPromptVisible = false

    private val watcher = ShortFormListener(
        object : ShortFormCallback {
            override fun onEnter(app: ShortFormApp, sinceMs: Long) {
                Logger.d("▶️ Enter short-form: ${app.label}")
                serviceScope.launch {
                    sessionMutex.withLock {
                        if (sessionId == null) {
                            try {
                                sessionId = repo.startSession(app.label)
                            } catch (e: Exception) {
                                Logger.e("startSession failed", e)
                            }
                        }
                    }
                }
            }

            override fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {
                Logger.d("⏹ Exit short-form: ${app.label}")
                serviceScope.launch {
                    sessionMutex.withLock {
                        sessionId?.let { id ->
                            try {
                                repo.endSession(id)
                            } catch (e: Exception) {
                                Logger.e("endSession failed", e)
                            } finally {
                                sessionId = null
                            }
                        }
                    }
                }
                isPromptVisible = false
            }

            override fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {
                if (!interventionEnabled) return
                if (isPromptVisible) return
                val remain = (lastChatAt + cooltimeMs - nowMs).coerceAtLeast(0L)
                if (remain > 0L) {
                    //Logger.d(remain.toString())
                    return
                }
                showPrompt()
            }
        },
        stableMs = 150L,
        exitGraceMs = 500L,
        tickIntervalMs = 100L
    )

    private val promptResultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            val reason = resultData?.getString("reason") ?: "unknown"
            Logger.d("ChatActivity closed. reason=$reason, resultCode=$resultCode")
            reloadCooltime()
            isPromptVisible = false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("[AccessibilityService] 연결됨")

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
        val root = rootInActiveWindow ?: return
        watcher.onEvent(event, root, time.nowMs())
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

    private fun showPrompt() {
        if (isPromptVisible) {
            Logger.d("❌ showPrompt: already visible, skip")
            return
        }
        isPromptVisible = true
        lastChatAt = time.nowMs()

        val i = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("receiver", promptResultReceiver)
            putExtra("sessionId", sessionId?.value ?: "")
        }
        startActivity(i)
    }

    private fun reloadCooltime() {
        lastChatAt = time.nowMs()
    }
}