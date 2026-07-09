package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.intervention.listener.instagram.InstagramScreen

class SMListener {
    private val screenTracker = DetectionScreen()
    private val appDurationTracker = ScreenDurationTracker<SocialMediaApp>()
    private val screenDurationTracker = ScreenDurationTracker<InstagramScreen>()

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis(),
        onUpdate: ((
            screenLabel: String,
            screenElapsedMs: Long,
            appLabel: String,
            appElapsedMs: Long
        ) -> Unit)? = null
    ) {
        if (root == null) {
            return
        }
        val pkg = event.packageName?.toString()
        val app = pkg?.let { SocialMediaApp.fromPackageName(it) }
        if (app == null) {
            return
        }

        if (appDurationTracker.current() == SocialMediaApp.INSTAGRAM && app != SocialMediaApp.INSTAGRAM) {
            appDurationTracker.forceReset(nowMs)
            screenDurationTracker.forceReset(nowMs)
        }

        val result = screenTracker.detectPassiveApp(pkg, root)
        val screenName: InstagramScreen? = result.screen
        val appName: SocialMediaApp? = result.app

        val appElapsedMs = appDurationTracker.update(appName, nowMs)
        val screenElapsedMs = screenDurationTracker.update(screenName, nowMs)

//        Logger.d(
//            "screen: $screenName (${screenElapsedMs}ms), app: $appName (${appElapsedMs}ms)"
//        )

        onUpdate?.invoke(
            screenName?.toString() ?: "NONE",
            screenElapsedMs,
            appName?.toString() ?: "NONE",
            appElapsedMs
        )
    }
}