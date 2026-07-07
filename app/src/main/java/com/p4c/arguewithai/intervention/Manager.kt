package com.p4c.arguewithai.intervention

import android.content.Context
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.SMCallback
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.SMListener
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.ShortFormApp
import com.p4c.arguewithai.repository.SessionId
import com.p4c.arguewithai.repository.SessionRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Manager(
    private val context: Context,
    private val repo: SessionRepository,
    private val serviceScope: CoroutineScope,
    private val sessionMutex: Mutex,
) {
    var sessionId: SessionId? = null
    var isPromptVisible: Boolean = false
    var currentWatchTime: Long = 0
    var watchTimeOnOneSession: Long = 0

    val shortFormTimeCounter = SMListener(
        object : SMCallback {
            override fun onEnter(app: ShortFormApp, sinceMs: Long) {
                Logger.d("⏹ Enter short-form: ${app.label}")
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
}
