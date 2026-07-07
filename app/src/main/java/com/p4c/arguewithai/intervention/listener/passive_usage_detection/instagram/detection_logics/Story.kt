package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Story {
    fun isStoryScreen(root: AccessibilityNodeInfo): Boolean {
        val viewerRoot = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/reel_viewer_root"
        )
        val progressBar = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/reel_viewer_progress_bar"
        )

        return viewerRoot.isNotEmpty() && progressBar.isNotEmpty()
    }
}