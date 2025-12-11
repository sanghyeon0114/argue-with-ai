package com.p4c.arguewithai.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class SessionApp(val pkg: String?, val label: String) {
    YOUTUBE("com.google.android.youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    TIKTOK("com.ss.android.ugc.trill", "TikTok"),
    MYAPP("com.p4c.arguewithai", "ArgueWithAi"),
    SYSTEM("com.android.systemui", "SYSTEM"),
    KEYBOARD("com.samsung.android.honeyboard", "KEYBOARD"),
    NULL(null, "NULL")
}

interface SessionViewCallback {
    fun onEnter(app: SessionApp, sinceMs: Long) {}
    fun onExit(app: SessionApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: SessionApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}

class SessionViewListener(
    private val callback: SessionViewCallback,
    private val stableMs: Long = 300L,
    private val exitGraceMs: Long = 500L,
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
        if (event == null) {
            maybeExitOnInvisibility(nowMs)
            return
        }

        val pkg = event.packageName?.toString()

        if (!isAllowedPackage(pkg)) {
            currentApp?.let { app ->
                callback.onExit(app, enteredAt, nowMs)
            }
            resetState()
            return
        }

        if (root == null) {
            maybeExitOnInvisibility(nowMs)
            return
        }

        val enterApp = detectEnter(pkg, root)

        if (enterApp != null) {
            lastSeenAppAt = nowMs
            handleDetected(enterApp, nowMs)
            maybeWatchingTick(nowMs)
            return
        }

        val exitApp = detectExit(pkg, root)
        if (exitApp != null) {
            maybeExitOnInvisibility(nowMs)
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
            resetState()
        }
    }

    private fun isAllowedPackage(pkg: String?): Boolean {
        return when (pkg) {
            SessionApp.YOUTUBE.pkg,
            SessionApp.INSTAGRAM.pkg,
            SessionApp.TIKTOK.pkg,
            SessionApp.MYAPP.pkg,
            SessionApp.SYSTEM.pkg,
            SessionApp.KEYBOARD.pkg,
            null -> true
            else -> false
        }
    }

    private fun resetState() {
        currentApp = null
        enteredAt = 0L
        pendingApp = null
        pendingSince = 0L
        lastSeenAppAt = 0L
        lastTickAt = 0L
    }

    private fun detectEnter(pkg: String?, root: AccessibilityNodeInfo): SessionApp? {
        return when (pkg) {
            SessionApp.YOUTUBE.pkg -> SessionApp.YOUTUBE
            SessionApp.INSTAGRAM.pkg -> SessionApp.INSTAGRAM
            SessionApp.TIKTOK.pkg -> SessionApp.TIKTOK
            else -> null
        }
    }

    private fun detectExit(pkg: String?, root: AccessibilityNodeInfo): SessionApp? {
        return when (pkg) {
            SessionApp.YOUTUBE.pkg -> SessionApp.YOUTUBE
            SessionApp.INSTAGRAM.pkg -> SessionApp.INSTAGRAM
            SessionApp.TIKTOK.pkg -> SessionApp.TIKTOK
            SessionApp.MYAPP.pkg -> if (isChatActivity()) SessionApp.MYAPP else null
            SessionApp.SYSTEM.pkg -> SessionApp.SYSTEM
            SessionApp.KEYBOARD.pkg -> SessionApp.KEYBOARD
            SessionApp.NULL.pkg -> SessionApp.NULL
            else -> null
        }
    }

    private fun isChatActivity(): Boolean {
        return com.p4c.arguewithai.chat.ChatActivityStatus.isOpen
    }
}
