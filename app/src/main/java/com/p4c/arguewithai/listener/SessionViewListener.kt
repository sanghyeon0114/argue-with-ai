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
    private val exitGraceMs: Long = 1000L,
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

        if (currentApp == null) {
            val detected = detectEnter(pkg)
            if (detected != null) {
                lastSeenAppAt = nowMs
                handleDetected(detected, nowMs)
                maybeWatchingTick(nowMs)
            }
        } else {
            val detectedApp = detectApp(pkg)

            if (detectedApp != null) {
                lastSeenAppAt = nowMs
                maybeWatchingTick(nowMs)
            } else {
                maybeExitOnInvisibility(nowMs)
            }
        }
    }

    private fun handleDetected(app: SessionApp, nowMs: Long) {
        if (currentApp != null) return

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
        if (lastTickAt == 0L) {
            lastTickAt = nowMs
            return
        }

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

    private fun detectEnter(pkg: String?): SessionApp? {
        return when (pkg) {
            SessionApp.YOUTUBE.pkg -> SessionApp.YOUTUBE
            SessionApp.INSTAGRAM.pkg -> SessionApp.INSTAGRAM
            SessionApp.TIKTOK.pkg -> SessionApp.TIKTOK
            SessionApp.MYAPP.pkg -> if (isChatActivity()) SessionApp.MYAPP else null
            else -> null
        }
    }

    private fun detectApp(pkg: String?): SessionApp? {
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
