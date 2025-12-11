package com.p4c.arguewithai.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class SessionApp(val pkg: String, val label: String) {
    YOUTUBE("com.google.android.youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    TIKTOK("com.ss.android.ugc.trill", "TikTok"),
    MYAPP("com.p4c.arguewithai", "ArgueWithAi")
}

interface SessionViewCallback {
    fun onEnter(app: SessionApp, sinceMs: Long) {}
    fun onExit(app: SessionApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: SessionApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}

class SessionViewListener(
    private val callback: SessionViewCallback,
    private val stableMs: Long = 300L,
    private val exitGraceMs: Long = 20*1000L,
    private val tickIntervalMs: Long = 500L
) {

    private var currentApp: SessionApp? = null
    private var enteredAt: Long = 0L

    private var pendingApp: SessionApp? = null
    private var pendingSince: Long = 0L
    private var lastSeenAppAt: Long = 0L

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
        val detected = detectApp(pkg, root)

        if (detected != null) {
            lastSeenAppAt = nowMs
            handleDetected(detected, nowMs)
            maybeWatchingTick(nowMs)
        } else {
            maybeExitOnInvisibility(nowMs)
        }
    }

    private fun handleDetected(app: SessionApp, nowMs: Long) {
        if (currentApp != null) {
            currentApp = app
            return
        }

        if (pendingApp != app) {
            pendingApp = app
            pendingSince = nowMs
            return
        }

        if (nowMs - pendingSince >= stableMs) {
            currentApp = app
            enteredAt = pendingSince
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
        if (lastSeenAppAt == 0L) return

        if (nowMs - lastSeenAppAt >= exitGraceMs) {
            callback.onExit(app, enteredAt, nowMs)
            currentApp = null
            pendingApp = null
            pendingSince = 0L
            lastSeenAppAt = 0L
            lastTickAt = 0L
        }
    }

    private fun detectApp(pkg: String?, root: AccessibilityNodeInfo): SessionApp? {
        return when (pkg) {
            SessionApp.YOUTUBE.pkg ->
                if (isYoutubeShortsScreen(root)) SessionApp.YOUTUBE else null

            SessionApp.INSTAGRAM.pkg ->
                if (isInstagramReelsScreen(root)) SessionApp.INSTAGRAM else null

            SessionApp.TIKTOK.pkg ->
                if (isTikTokScreen(root)) SessionApp.TIKTOK else null

            SessionApp.MYAPP.pkg ->
                if (isChatActivity()) SessionApp.MYAPP else null

            else -> null
        }
    }

    private fun isYoutubeShortsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.view.View" &&
                node.viewIdResourceName?.endsWith("reel_progress_bar") == true
            ) found++

            if (node.className == "android.widget.FrameLayout" &&
                node.viewIdResourceName?.endsWith("reel_player_page_container") == true
            ) found++

            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName?.endsWith("reel_time_bar") == true
            ) found++
        }

        return found >= 2
    }

    private fun isInstagramReelsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_author_info_component"
            ) {
                found++
            }

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_author_username"
            ) {
                found++
            }

            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_caption_component"
            ) {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/like_button"
            ) {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/direct_share_button"
            ) {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_ufi_more_button_component"
            ) {
                found++
            }
        }
        return found >= 5
    }

    private fun isTikTokScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/ew0"
            ) found++

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/dnl"
            ) found++

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/ggg"
            ) found++
        }

        return found >= 3
    }
    private fun isChatActivity(): Boolean {
        return com.p4c.arguewithai.chat.ChatActivityStatus.isOpen
    }

    private inline fun AccessibilityNodeInfo.walkNodes(visit: (AccessibilityNodeInfo) -> Unit) {
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