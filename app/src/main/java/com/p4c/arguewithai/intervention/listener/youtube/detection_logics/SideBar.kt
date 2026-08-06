package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object SideBar {
    fun isSideBar(root: AccessibilityNodeInfo): Boolean {
        return hasListItemText(root) || hasListItemText2(root) || hasListItemText3(root) || hasCommentSortMenu(root)
    }
    fun hasListItemText(root: AccessibilityNodeInfo): Boolean {
        val listItemTextId = "${YoutubeLogics.YOUTUBE_PKG}:id/list_item_text"
        val nodes = root.findAccessibilityNodeInfosByViewId(listItemTextId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }

    fun hasListItemText2(root: AccessibilityNodeInfo): Boolean {
        val listItemTextId = "${YoutubeLogics.YOUTUBE_PKG}:id/bottom_sheet_list"
        val nodes = root.findAccessibilityNodeInfosByViewId(listItemTextId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
    fun hasListItemText3(root: AccessibilityNodeInfo): Boolean {
        val listItemTextId = "${YoutubeLogics.YOUTUBE_PKG}:id/title"
        val nodes = root.findAccessibilityNodeInfosByViewId(listItemTextId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }

    fun hasCommentSortMenu(root: AccessibilityNodeInfo): Boolean {
        val textId = "${YoutubeLogics.YOUTUBE_PKG}:id/text"

        val texts = root.findAccessibilityNodeInfosByViewId(textId)
            ?.filter { it.isVisibleToUser }
            ?.mapNotNull { it.text?.toString() }
            ?: emptyList()

        val hasSortOptions = texts.contains("인기순") && texts.contains("최신순")

        return hasSortOptions
    }
}