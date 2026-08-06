package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Creation {
    fun isCreationScreen(root: AccessibilityNodeInfo): Boolean {
        val creationModeButtonId = "${YoutubeLogics.YOUTUBE_PKG}:id/creation_mode_button"
        val nodes = root.findAccessibilityNodeInfosByViewId(creationModeButtonId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
}
