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
    PROFILE("profile")
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
            } else if(isInstagramNoTabSelected(root)) {
                Logger.d("HOME COLD START")
                InstagramScreen.HOME
            } else {
                Logger.d("NO")
                null
            }
            else -> null
        }
    }
    companion object {
        fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isHomeTabSelected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""
                if ((id.endsWith("feed_tab") && node.isSelected) ||
                    (id.endsWith("tab_icon") && node.isSelected && node.parent?.viewIdResourceName?.endsWith("feed_tab") == true)
                ) {
                    isHomeTabSelected = true
                }
            }

            return isHomeTabSelected
        }
        fun isInstagramReelsScreen(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isReelsTabSelected = false
            var hasReelsContainer = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""

                if ((id.endsWith("clips_tab") && node.isSelected) ||
                    (id.endsWith("tab_icon") && node.isSelected && node.parent?.viewIdResourceName?.endsWith("clips_tab") == true)
                ) {
                    isReelsTabSelected = true
                }

                if (id.endsWith("root_clips_layout") || id.endsWith("clips_viewer_video_layout")) {
                    hasReelsContainer = true
                }
            }

            return isReelsTabSelected || hasReelsContainer
        }
        fun isInstagramDirectScreen(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isDirectTabSelected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""

                if ((id.endsWith("direct_tab") && node.isSelected) ||
                    (id.endsWith("tab_icon") && node.isSelected && node.parent?.viewIdResourceName?.endsWith("direct_tab") == true)
                ) {
                    isDirectTabSelected = true
                }
            }

            return isDirectTabSelected
        }
        fun isInstagramSearchScreen(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isSearchTabSelected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""

                if ((id.endsWith("search_tab") && node.isSelected) ||
                    (id.endsWith("tab_icon") && node.isSelected && node.parent?.viewIdResourceName?.endsWith("search_tab") == true)
                ) {
                    isSearchTabSelected = true
                }
            }

            return isSearchTabSelected
        }
        fun isInstagramProfileScreen(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isProfileTabSelected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""

                if ((id.endsWith("profile_tab") && node.isSelected) ||
                    (id.endsWith("tab_avatar") && node.isSelected && node.parent?.viewIdResourceName?.endsWith("container") == true)
                ) {
                    isProfileTabSelected = true
                }
            }

            return isProfileTabSelected
        }
        fun isInstagramNoTabSelected(root: AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            var isAnyTabSelected = false

            root.walkNodes { node ->
                val id = node.viewIdResourceName ?: ""
                val isSelected = node.isSelected

                if (id.endsWith("feed_tab")) {
                    if (isSelected) isAnyTabSelected = true
                }
                if (id.endsWith("clips_tab")) {
                    if (isSelected) isAnyTabSelected = true
                }
                if (id.endsWith("direct_tab")) {
                    if (isSelected) isAnyTabSelected = true
                }
                if (id.endsWith("search_tab")) {
                    if (isSelected) isAnyTabSelected = true
                }
                if (id.endsWith("profile_tab")) {
                    if (isSelected) isAnyTabSelected = true
                }
            }

            return !isAnyTabSelected
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