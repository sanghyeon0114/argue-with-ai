package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.InstagramLogics.INSTAGRAM_PKG

object Search {
    fun isSearchScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isSearchTab(root)
    }

    private fun isSearchTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "search_tab")
    }
    private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
        val fullId = "$INSTAGRAM_PKG:id/$tabIdSuffix"
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