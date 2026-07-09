package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.intervention.listener.instagram.InstagramScreen
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val screenTracker = DetectionScreen()
    private val appDurationTracker = ScreenDurationTracker<SocialMediaApp>()
    private val screenDurationTracker = ScreenDurationTracker<InstagramScreen>()

    private var pkgNullSinceMs: Long? = null
    private var appMismatchSinceMs: Long? = null

    companion object {
        private const val PKG_NULL_RESET_THRESHOLD_MS = 500L
        private const val APP_MISMATCH_RESET_THRESHOLD_MS = 500L
    }

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
        if (pkg == null) {
            return
        }
        val app = pkg.let { SocialMediaApp.fromPackageName(it) }

        if (app == null) {
            if (pkgNullSinceMs == null) {
                pkgNullSinceMs = nowMs
            }
            val nullElapsed = nowMs - pkgNullSinceMs!!
            if (nullElapsed >= PKG_NULL_RESET_THRESHOLD_MS) {
                appDurationTracker.forceReset(nowMs)
                screenDurationTracker.forceReset(nowMs)
            }
            return
        }
        pkgNullSinceMs = null

        if (appDurationTracker.current() == SocialMediaApp.INSTAGRAM && app != SocialMediaApp.INSTAGRAM) {
            if (appMismatchSinceMs == null) {
                appMismatchSinceMs = nowMs
            }
            val mismatchElapsed = nowMs - appMismatchSinceMs!!
            if (mismatchElapsed >= APP_MISMATCH_RESET_THRESHOLD_MS) {
                appDurationTracker.forceReset(nowMs)
                screenDurationTracker.forceReset(nowMs)
                appMismatchSinceMs = null
            }
        } else {
            appMismatchSinceMs = null
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