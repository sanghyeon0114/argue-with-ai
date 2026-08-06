package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Main {
        fun isMainScreen(root: AccessibilityNodeInfo): Boolean {
            return isYoutubeContainerScreen(root)
        }
        fun isYoutubeContainerScreen(root: AccessibilityNodeInfo): Boolean {
        val containerId = "${YoutubeLogics.YOUTUBE_PKG}:id/more_drawer_container"
        val nodes = root.findAccessibilityNodeInfosByViewId(containerId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }

}