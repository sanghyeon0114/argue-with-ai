package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.intervention.listener.instagram.PassiveDetectionResult

class SMListener {
    private val screenTracker = DetectionScreen()

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        nowMs: Long = System.currentTimeMillis(),
    ): PassiveDetectionResult? {
        val pkg = event.packageName?.toString()
        if(pkg == null) {
            return null
        }
        val app = SocialMediaApp.fromPackageName(pkg)
        if(app == null) {
            return null
        }

        return screenTracker.detectPassiveApp(pkg, root, nowMs)
    }
}