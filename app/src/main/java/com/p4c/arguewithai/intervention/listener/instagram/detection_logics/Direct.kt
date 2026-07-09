package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Direct {
    fun isDirectScreen(root: AccessibilityNodeInfo): Boolean {
        return isDirectTab(root)
    }
    private fun isDirectTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "direct_tab")
    }
    private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
        val fullId = "${InstagramLogics.INSTAGRAM_PKG}:id/$tabIdSuffix"
        val tabs = root.findAccessibilityNodeInfosByViewId(fullId) ?.filter { it.isVisibleToUser } ?: return false

        return tabs.any { tab ->
            tab.isSelected ||
                    (0 until tab.childCount).any { i ->
                        val child = tab.getChild(i) ?: return@any false
                        val childId = child.viewIdResourceName ?: ""
                        iconIdSuffixes.any { childId.endsWith(it) } && child.isSelected
                    }
        }
    }
}