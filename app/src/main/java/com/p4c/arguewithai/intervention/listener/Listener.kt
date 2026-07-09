package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val screenTracker = DetectionScreen()
    private val durationTracker = ScreenDurationTracker<SocialMediaApp>()

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis(),
        onUpdate: ((screen: String, screenElapsedMs: Long) -> Unit)? = null
    ) {
        if (root == null) {
            return
        }
        val pkg = event.packageName?.toString()
        val app = pkg?.let { SocialMediaApp.fromPackageName(it) }
        if (app == null) {
            return
        }

        if (durationTracker.current() == SocialMediaApp.INSTAGRAM && app != SocialMediaApp.INSTAGRAM) {
            durationTracker.forceReset(nowMs)
        }

        val detected: SocialMediaApp? = screenTracker.detectPassiveApp(pkg, root)
        val elapsedMs = durationTracker.update(detected, nowMs)

//        Logger.d(
//            "detected: $detected, lastDetected: ${durationTracker.current()}, 유지 시간: ${elapsedMs}ms (${elapsedMs / 1000}s)"
//        )

        onUpdate?.invoke(detected?.toString() ?: "NONE", elapsedMs)
    }
}