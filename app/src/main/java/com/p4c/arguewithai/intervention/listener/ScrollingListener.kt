package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.utils.Logger

enum class ShortFormApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram")
}

interface AppScreen {
    val label: String
}

enum class InstagramScreen(override val label: String) : AppScreen {
    HOME("home"),
    REELS("reels"),
    DM("dm"),
    SEARCH("search"),
    PROFILE("profile"),
    WATCH_REELS("watch_reels"),
    NULL("null")
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}

class ShortFormListener(
    private val callback: ShortFormCallback,
    private val stableMs: Long = 150L,
    private val exitGraceMs: Long = 500L,
    private val tickIntervalMs: Long = 100L
) {

    private var currentApp: ShortFormApp? = null
    private var enteredAt: Long = 0L

    private var pendingApp: ShortFormApp? = null
    private var pendingSince: Long = 0L

    private var lastSeenShortFormAt: Long = 0L
    private var lastTickAt: Long = 0L

    fun onEvent(
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (event == null || root == null) {
            maybeExitOnInvisibility(nowMs)
            return
        }

        val pkg = event.packageName?.toString()
        val detected: ShortFormApp? = detectApp(pkg, root)

        if (detected != null) {
            lastSeenShortFormAt = nowMs
            handleDetected(detected, nowMs)
            maybeWatchingTick(nowMs)
        } else {
            maybeExitOnInvisibility(nowMs)
        }
    }
    private fun handleDetected(app: ShortFormApp, nowMs: Long) {
        if (currentApp == app) return

        if (pendingApp != app) {
            pendingApp = app
            pendingSince = nowMs
            return
        }

        if (nowMs - pendingSince >= stableMs) {
            currentApp?.let { prev ->
                callback.onExit(prev, enteredAt, nowMs)
            }
            currentApp = app
            enteredAt = nowMs
            lastTickAt = nowMs
            callback.onEnter(app, enteredAt)
        }
    }

    private fun maybeWatchingTick(nowMs: Long) {
        val app = currentApp ?: return
        if (nowMs - lastTickAt >= tickIntervalMs) {
            val elapsed = nowMs - enteredAt
            callback.onWatchingTick(app, enteredAt, nowMs, elapsed)
            lastTickAt = nowMs
        }
    }

    private fun maybeExitOnInvisibility(nowMs: Long) {
        val app = currentApp ?: return
        if (nowMs - lastSeenShortFormAt >= exitGraceMs) {
            callback.onExit(app, enteredAt, nowMs)
            currentApp = null
            pendingApp = null
            lastTickAt = 0L
        }
    }

    private fun detectApp(pkg: String?, root: AccessibilityNodeInfo): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if(detectInstagramScreen(pkg, root) == InstagramScreen.HOME || detectInstagramScreen(pkg, root) == InstagramScreen.REELS || detectInstagramScreen(pkg, root) == InstagramScreen.SEARCH) ShortFormApp.INSTAGRAM else null
            else -> null
        }
    }
    private fun detectInstagramScreen(pkg: String?, root: AccessibilityNodeInfo): AppScreen? {
//        if(isTestScreenDetection(root)) {
//            Logger.d("TEST")
//        }
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
    companion object {
        private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
            var selected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""
                if (id.endsWith(tabIdSuffix)) {
                    if (node.isSelected) {
                        selected = true
                    } else {
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            val childId = child.viewIdResourceName ?: ""
                            if (iconIdSuffixes.any { childId.endsWith(it) } && child.isSelected) {
                                selected = true
                            }
                        }
                    }
                }
            }

            return selected
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

        fun isTestScreenDetection(root: AccessibilityNodeInfo?): Boolean {
            return true
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
    }
}