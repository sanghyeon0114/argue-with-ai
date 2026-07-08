package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.instagram.Tracker

class SMListener(
    private val callback: SMCallback,
) {
    private val screenTracker = Tracker()

    fun onEvent(
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis(),
        onScreenChanged: ((String) -> Unit)? = {}
    ) {
        if(root == null || event == null) {
            return
        }
        val pkg = event.packageName?.toString()
        val detected: ShortFormApp? = screenTracker.detectPassiveApp(pkg, root, onScreenChanged)

        if(detected != null) {

        }
    }
}