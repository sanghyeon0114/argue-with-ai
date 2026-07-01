package com.p4c.arguewithai.intervention.listener.scroll

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.utils.Logger

object Logics {
    private const val IG_PKG = "com.instagram.android"
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, windowList: List<AccessibilityWindowInfo>?): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if(detectInstagramScreen(pkg, root) == InstagramScreen.HOME || detectInstagramScreen(pkg, root) == InstagramScreen.REELS || detectInstagramScreen(pkg, root) == InstagramScreen.SEARCH) ShortFormApp.INSTAGRAM else null
            else -> null
        }
    }
    private fun detectInstagramScreen(pkg: String?, root: AccessibilityNodeInfo): AppScreen? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if (isInstagramHomeScreen(root)) {
                //Logger.d("HOME")
                InstagramScreen.HOME
            } else if (isInstagramReelsScreen(root)) {
                //Logger.d("REELS")
                InstagramScreen.REELS
            } else if (isInstagramDirectScreen(root)) {
                //Logger.d("DM")
                InstagramScreen.DM
            } else if (isInstagramSearchScreen(root)) {
                //Logger.d("SEARCH")
                InstagramScreen.SEARCH
            } else if (isInstagramProfileScreen(root)) {
                //Logger.d("Profile")
                InstagramScreen.PROFILE
            } else {
                //Logger.d("NONE")
                null
            }
            else -> null
        }
    }
    fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isTabSelected(root, "feed_tab")
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
}