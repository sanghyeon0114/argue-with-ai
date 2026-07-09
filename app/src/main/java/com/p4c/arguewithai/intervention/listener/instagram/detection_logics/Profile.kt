package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Profile {
    fun isProfileScreen(root: AccessibilityNodeInfo): Boolean {
        return isProfileTab(root)
    }
    private fun isProfileTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "profile_tab")
    }
    private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
        val fullId = "${InstagramLogics.INSTAGRAM_PKG}:id/$tabIdSuffix"
        val tabs = root.findAccessibilityNodeInfosByViewId(fullId).filter { it.isVisibleToUser }

        return tabs.any { tab ->
            tab.isSelected ||
                    (0 until tab.childCount).any { i ->
                        val child = tab.getChild(i) ?: return@any false
                        val childId = child.viewIdResourceName ?: ""
                        iconIdSuffixes.any { childId.endsWith(it) } && child.isSelected
                    }
        }
    }

    fun isSubscriberListScreen(root: AccessibilityNodeInfo): Boolean {
        val followListTabId = "${InstagramLogics.INSTAGRAM_PKG}:id/unified_follow_list_tab_layout"
        val titleId = "${InstagramLogics.INSTAGRAM_PKG}:id/title"

        val hasFollowListTab = root.findAccessibilityNodeInfosByViewId(followListTabId).any { it.isVisibleToUser }
        val hasSubscriberTitle = root.findAccessibilityNodeInfosByViewId(titleId).any { it.isVisibleToUser && it.text?.toString()?.contains("구독") == true }

        return hasFollowListTab && hasSubscriberTitle
    }

    fun isOtherProfileScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val containerId = "${InstagramLogics.INSTAGRAM_PKG}:id/profile_header_container"
        val hasProfileHeader = root.findAccessibilityNodeInfosByViewId(containerId).any { it.isVisibleToUser }

        return hasProfileHeader
    }
    fun isOtherSubscribeListScreen(root: AccessibilityNodeInfo): Boolean {
        val followListTabId = "${InstagramLogics.INSTAGRAM_PKG}:id/unified_follow_list_tab_layout"
        val titleId = "${InstagramLogics.INSTAGRAM_PKG}:id/title"

        val recommendTargets = setOf("추천")
        val hasFollowListTab = root.findAccessibilityNodeInfosByViewId(followListTabId).any { it.isVisibleToUser }
        val hasRecommendTitle = root.findAccessibilityNodeInfosByViewId(titleId).any { it.isVisibleToUser && it.text?.toString() in recommendTargets }

        return hasFollowListTab && hasRecommendTitle
    }
}