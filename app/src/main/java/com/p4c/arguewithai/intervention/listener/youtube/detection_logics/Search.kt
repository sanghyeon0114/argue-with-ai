package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Search {
    fun isSearchScreen(root: AccessibilityNodeInfo): Boolean {
        val searchBoxId = "${YoutubeLogics.YOUTUBE_PKG}:id/search_box"
        val nodes = root.findAccessibilityNodeInfosByViewId(searchBoxId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
}