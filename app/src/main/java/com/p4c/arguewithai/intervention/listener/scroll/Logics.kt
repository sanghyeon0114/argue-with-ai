package com.p4c.arguewithai.intervention.listener.scroll

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.utils.Logger

object Logics {
    private const val IG_PKG = "com.instagram.android"
    private var lastScreen: AppScreen? = null
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, windowList: List<AccessibilityWindowInfo>?): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if(detectInstagramScreen(pkg, root) == InstagramScreen.HOME || detectInstagramScreen(pkg, root) == InstagramScreen.REELS || detectInstagramScreen(pkg, root) == InstagramScreen.SEARCH) ShortFormApp.INSTAGRAM else null
            else -> null
        }
    }
    private fun detectInstagramScreen(pkg: String?, root: AccessibilityNodeInfo): AppScreen? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> {
                val screen: AppScreen? = if (isInstagramHomeScreen(root)) {
                    InstagramScreen.HOME
                } else if (isInstagramReelsScreen(root)) {
                    InstagramScreen.REELS
                } else if (isInstagramDirectScreen(root)) {
                    InstagramScreen.DM
                } else if (isInstagramSearchScreen(root)) {
                    InstagramScreen.SEARCH
                } else if (isInstagramProfileScreen(root)) {
                    InstagramScreen.PROFILE
                } else if (isInstagramFeedMenuScreen(root)) {
                    InstagramScreen.FEED_MENU
                } else {
                    null
                }
                if (screen != lastScreen) {
                    Logger.d(screen?.toString() ?: "NONE")
                    lastScreen = screen
                }
                screen
            }
            else -> null
        }
    }
    fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (isTabSelected(root, "feed_tab")) return true
        if (hasVisibleNodeById(root, "title_logo")) return true
        return hasFeedActionButton(root)
    }
    fun isInstagramReelsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isTabSelected(root, "clips_tab")
    }
    fun isInstagramDirectScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isTabSelected(root, "direct_tab")
    }
    fun isInstagramSearchScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isTabSelected(root, "search_tab")
    }
    fun isInstagramProfileScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isTabSelected(root, "profile_tab")
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
        val fullId = "$IG_PKG:id/$tabIdSuffix"
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
    private fun hasVisibleNodeById(root: AccessibilityNodeInfo, idSuffix: String): Boolean {
        val fullId = "$IG_PKG:id/$idSuffix"
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
    private fun hasFeedActionButton(root: AccessibilityNodeInfo): Boolean {
        val buttonIds = listOf(
            "row_feed_button_like",
            "row_feed_button_comment",
            "row_feed_button_share",
            "row_feed_button_save",
            "row_feed_photo_profile_imageview",
            "row_feed_photo_profile_name"
        )
        return buttonIds.any { hasVisibleNodeById(root, it) }
    }
    fun isInstagramFeedMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val labels = root.findAccessibilityNodeInfosByViewId("$IG_PKG:id/context_menu_item_label")
            ?.filter { it.isVisibleToUser } ?: return false
        val targets = setOf("팔로잉", "즐겨찾기")
        return labels.any { it.text?.toString() in targets }
    }
}