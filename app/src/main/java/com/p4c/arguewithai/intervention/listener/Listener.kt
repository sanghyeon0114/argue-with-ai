package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.InstagramTracker
import com.p4c.arguewithai.intervention.listener.youtube.YoutubeTracker
import com.p4c.arguewithai.utils.Logger

class SMListener {
    private val instagramTracker = InstagramTracker()
    private val youtubeTracker = YoutubeTracker()
    fun onEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        nowMs: Long = System.currentTimeMillis(),
    ): PassiveDetectionResult? {
        val pkg = event.packageName?.toString()
        if(pkg == null) {
            return null
        }
        //Logger.d("$pkg")
        var result: PassiveDetectionResult? = instagramTracker.getScreenInformation(pkg, root, nowMs)
        if(result == null) {
            result = youtubeTracker.getScreenInformation(pkg, root, nowMs)

        }
        Logger.d("result : $result")
        return result
    }
}