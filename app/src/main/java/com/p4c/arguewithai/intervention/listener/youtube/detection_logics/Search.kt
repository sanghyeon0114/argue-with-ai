package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Search {
    fun isSearchScreen(root: AccessibilityNodeInfo): Boolean {
        val searchLayoutId = "${YoutubeLogics.YOUTUBE_PKG}:id/search_layout"
        val searchEditTextId = "${YoutubeLogics.YOUTUBE_PKG}:id/search_edit_text"

        val layoutNodes = root.findAccessibilityNodeInfosByViewId(searchLayoutId) ?: return false
        val editTextNodes = root.findAccessibilityNodeInfosByViewId(searchEditTextId) ?: return false

        val isLayoutVisible = layoutNodes.any { it.isVisibleToUser }
        val isEditTextVisible = editTextNodes.any { it.isVisibleToUser }

        return isLayoutVisible && isEditTextVisible
    }
}