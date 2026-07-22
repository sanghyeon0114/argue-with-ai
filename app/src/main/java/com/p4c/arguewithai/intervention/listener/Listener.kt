package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.intervention.listener.instagram.DetectionScreen
import com.p4c.arguewithai.intervention.listener.instagram.PassiveDetectionResult
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val screenTracker = DetectionScreen()

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        window: AccessibilityWindowInfo?,
        nowMs: Long = System.currentTimeMillis(),
    ): PassiveDetectionResult? {
        val pkg = event.packageName?.toString()
        if(pkg == null) {
            return null
        }
        Logger.d("$pkg")
        return screenTracker.getScreenInformation(pkg, root, window, nowMs)
    }
}