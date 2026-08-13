package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.InstagramTracker
import com.p4c.arguewithai.intervention.listener.youtube.YoutubeTracker
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val instagramTracker = InstagramTracker()
    private val youtubeTracker = YoutubeTracker()
    private var lastResult: PassiveDetectionResult? = null
    fun resetPassive(nowMs: Long) {
        instagramTracker.resetPassive(nowMs)
        youtubeTracker.resetPassive(nowMs)
    }

    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        nowMs: Long = System.currentTimeMillis(),
        isKeyboardVisible: Boolean = false,
    ): PassiveDetectionResult? {
        val pkg = event.packageName?.toString()
        if(pkg == null) {
            return lastResult
        }
        var result: PassiveDetectionResult? = instagramTracker.getScreenInformation(pkg, root, nowMs, isKeyboardVisible)
        if(result == null) {
            result = youtubeTracker.getScreenInformation(pkg, root, nowMs, isKeyboardVisible)
        }
        lastResult = result
        return result
    }
}
