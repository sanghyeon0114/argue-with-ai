package com.p4c.arguewithai.intervention

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.p4c.arguewithai.chat.ChatActivity
import com.p4c.arguewithai.listener.SessionApp
import com.p4c.arguewithai.listener.SessionViewCallback
import com.p4c.arguewithai.listener.SessionViewListener
import com.p4c.arguewithai.listener.ShortFormApp
import com.p4c.arguewithai.listener.ShortFormCallback
import com.p4c.arguewithai.listener.ShortFormListener
import com.p4c.arguewithai.repository.SessionId
import com.p4c.arguewithai.repository.SessionRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ShortFormWatcherManager(
    private val context: Context,
    private val repo: SessionRepository,
    private val serviceScope: CoroutineScope,
    private val sessionMutex: Mutex,
) {
    var sessionId: SessionId? = null
    var isPromptVisible: Boolean = false
    var interventionEnabled: Boolean = true
    var suppressUntilSessionExit: Boolean = false
    var lastTotalScore: Int = 0
    var cooltimeMs: Long = 10 * 60 * 1000L
    var currentWatchTime: Long = 0
    var watchTimeOnOneSession: Long = 0

    val shortFormTimeCounter = ShortFormListener(
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

                watchTimeOnOneSession += currentWatchTime
                currentWatchTime = 0
            }

            override fun onWatchingTick(
                app: ShortFormApp,
                enteredAtMs: Long,
                nowMs: Long,
                elapsedMs: Long
            ) {
                currentWatchTime = elapsedMs
            }
        }
    )

    val sessionWatcher = SessionViewListener(
        object : SessionViewCallback {
            override fun onEnter(app: SessionApp, sinceMs: Long) {
                Logger.d("▶️▶️▶️▶️ Enter Session View: ${app.label}, sinceMs=$sinceMs")
            }

            override fun onExit(app: SessionApp, enteredAtMs: Long, exitedAtMs: Long) {
                Logger.d("▶️▶️▶️▶️ Exit Session View: ${app.label}, enteredAt=$enteredAtMs, exitedAt=$exitedAtMs")

                suppressUntilSessionExit = false
                lastTotalScore = 0

                watchTimeOnOneSession = 0
            }

            override fun onWatchingTick(
                app: SessionApp,
                enteredAtMs: Long,
                nowMs: Long,
                elapsedMs: Long
            ) {
                if (!interventionEnabled) return
                if (isPromptVisible) return

                if (suppressUntilSessionExit) return

                var totalWatchTime = currentTotalWatchTime()

                if(totalWatchTime >= cooltimeMs) {
                    watchTimeOnOneSession = 0
                    currentWatchTime = 0
                    showPrompt()
                }
            }
        }
    )

    private fun currentTotalWatchTime(): Long {
        return watchTimeOnOneSession + currentWatchTime
    }

    private val promptResultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            val reason = resultData?.getString("reason") ?: "unknown"
            val score = resultData?.getInt("totalScore", 0) ?: 0
            lastTotalScore = score
            suppressUntilSessionExit = (score > 0)

            Logger.d("ChatActivity closed. reason=$reason, resultCode=$resultCode, totalScore=$score")
            isPromptVisible = false
        }
    }

    private fun showPrompt() {
        if (isPromptVisible) {
            Logger.d("❌ showPrompt: already visible, skip")
            return
        }
        isPromptVisible = true

        val i = Intent(context, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("receiver", promptResultReceiver)
            putExtra("session_id", sessionId?.value ?: "")
        }
        context.startActivity(i)
    }
}
