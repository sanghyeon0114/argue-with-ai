package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val screenTracker = DetectionScreen()

    private var screenEnteredAtMs: Long = 0L
    private var currentScreenLabel: String = "NONE"

    private var totalPassiveMs: Long = 0L
    private var lastPassiveTickMs: Long? = null

    private var pendingScreenResetAtMs: Long? = null

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis(),
        onUpdate: ((screen: String, screenElapsedMs: Long, totalPassiveMs: Long) -> Unit)? = null
    ) {
        if (root == null) {
            handlePotentialScreenReset(nowMs, onUpdate)
            return
        }
        val pkg = event.packageName?.toString()
        val app = pkg?.let { SocialMediaApp.fromPackageName(it) }
        if (app == null) {
            handlePotentialScreenReset(nowMs, onUpdate)
            return
        }

        pendingScreenResetAtMs = null

        val detected: SocialMediaApp? = screenTracker.detectPassiveApp(pkg, root) { label ->
            currentScreenLabel = label
            screenEnteredAtMs = nowMs
        }

        if (detected != null) {
            val screenElapsedMs = nowMs - screenEnteredAtMs

            val lastTick = lastPassiveTickMs
            if (lastTick != null) {
                totalPassiveMs += (nowMs - lastTick)
            }
            lastPassiveTickMs = nowMs

            Logger.d(
                "화면: $currentScreenLabel / 유지시간: ${formatDuration(screenElapsedMs)} " +
                        "/ 전체 Passive 사용시간: ${formatDuration(totalPassiveMs)}"
            )

            onUpdate?.invoke(currentScreenLabel, screenElapsedMs, totalPassiveMs)
        } else {
            lastPassiveTickMs = null
            screenEnteredAtMs = nowMs

            onUpdate?.invoke(currentScreenLabel, 0L, totalPassiveMs)
        }
    }

    private fun handlePotentialScreenReset(
        nowMs: Long,
        onUpdate: ((screen: String, screenElapsedMs: Long, totalPassiveMs: Long) -> Unit)?
    ) {
        if (currentScreenLabel == "NONE") {
            return
        }

        val pendingSince = pendingScreenResetAtMs
        if (pendingSince == null) {
            pendingScreenResetAtMs = nowMs
            return
        }

        if (nowMs - pendingSince >= SCREEN_RESET_DEBOUNCE_MS) {
            resetCurrentScreenState(onUpdate)
            pendingScreenResetAtMs = null
        }
    }

    private fun resetCurrentScreenState(
        onUpdate: ((screen: String, screenElapsedMs: Long, totalPassiveMs: Long) -> Unit)?
    ) {
        screenTracker.reset()
        currentScreenLabel = "NONE"
        screenEnteredAtMs = 0L
        lastPassiveTickMs = null
        onUpdate?.invoke("NONE", 0L, totalPassiveMs)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    companion object {
        private const val SCREEN_RESET_DEBOUNCE_MS = 500L
    }
}