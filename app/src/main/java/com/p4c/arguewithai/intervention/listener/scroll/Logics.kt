package com.p4c.arguewithai.intervention.listener.scroll

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.utils.Logger

object Logics {
    private const val IG_PKG = "com.instagram.android"
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, windowList: List<AccessibilityWindowInfo>?): ShortFormApp? {

        return null
//        return when (pkg) {
//            ShortFormApp.INSTAGRAM.pkg -> if(detectInstagramScreen(pkg, root) == InstagramScreen.HOME || detectInstagramScreen(pkg, root) == InstagramScreen.REELS || detectInstagramScreen(pkg, root) == InstagramScreen.SEARCH) ShortFormApp.INSTAGRAM else null
//            else -> null
//        }
    }
    private fun detectInstagramScreen(pkg: String?, root: AccessibilityNodeInfo): AppScreen? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if (isInstagramHomeScreen(root)) {
                Logger.d("HOME")
                InstagramScreen.HOME
            } else if (isInstagramReelsScreen(root)) {
                Logger.d("REELS")
                InstagramScreen.REELS
            } else if (isInstagramDirectScreen(root)) {
                Logger.d("DM")
                InstagramScreen.DM
            } else if (isInstagramSearchScreen(root)) {
                Logger.d("SEARCH")
                InstagramScreen.SEARCH
            } else if (isInstagramProfileScreen(root)) {
                Logger.d("Profile")
                InstagramScreen.PROFILE
            } else {
                Logger.d("NONE")
                null
            }
            else -> null
        }
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
    fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val hasStoriesTray = findNode(root) { node ->
            node.contentDescription?.toString() == "릴스 트레이 컨테이너"
        } != null

        return hasStoriesTray
    }
    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }
    inline fun AccessibilityNodeInfo.walkNodes(visit: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            visit(node)

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
    }
//    fun dumpAllWindows(windowList: List<AccessibilityWindowInfo>?) {
//        if (windowList == null) return
//
//        val targetWindows = windowList.filter {
//            it.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
//                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
//        }
//
//        Logger.d("╔══════════ WINDOW DUMP (${targetWindows.size} windows) ══════════")
//        for ((index, window) in targetWindows.withIndex()) {
//            val root = window.root ?: continue
//            val type = if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) "KEYBOARD" else "APP"
//            Logger.d("╠═ Window[$index] type=$type layer=${window.layer}")
//            dumpTree(root)
//        }
//        Logger.d("╚══════════════════════════════════════════════")
//    }
//    fun dumpTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
//        if (node == null) return
//        val rect = Rect()
//        node.getBoundsInScreen(rect)
//        Logger.d("${"  ".repeat(depth)}" +
//                "${node.className} " +
//                "text=${node.text} " +
//                "id=${node.viewIdResourceName} " +
//                "bounds=$rect clickable=${node.isClickable}")
//        for (i in 0 until node.childCount) {
//            dumpTree(node.getChild(i), depth + 1)
//        }
//    }
}