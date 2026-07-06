package com.p4c.arguewithai.intervention.listener.session_time

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.chat.activity.BlockingActivityStatus
import com.p4c.arguewithai.chat.activity.RuleBasedChatbotActivityStatus
import com.p4c.arguewithai.chat.activity.LlmChatbotActivityStatus

class Listener(
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
            val detected = detectEnter(pkg, root)
            if (detected != null) {
                lastSeenAppAt = nowMs
                handleDetected(detected, nowMs)
                maybeWatchingTick(nowMs)
            }
        } else {
            val detectedApp = detectApp(pkg, root)

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

    private fun detectEnter(pkg: String?, root: AccessibilityNodeInfo): SessionApp? {
        return when (pkg) {
            SessionApp.MYAPP.pkg -> if (isChatActivity()) SessionApp.MYAPP else null
            else -> null
        }
    }

    private fun detectApp(pkg: String?, root: AccessibilityNodeInfo): SessionApp? {
        return when (pkg) {
            SessionApp.MYAPP.pkg -> if (isChatActivity()) SessionApp.MYAPP else null
            SessionApp.SYSTEM.pkg -> SessionApp.SYSTEM
            SessionApp.KEYBOARD.pkg -> SessionApp.KEYBOARD
            SessionApp.NULL.pkg -> SessionApp.NULL
            else -> null
        }
    }

    private fun isChatActivity(): Boolean {
        return BlockingActivityStatus.isOpen || RuleBasedChatbotActivityStatus.isOpen || LlmChatbotActivityStatus.isOpen
    }
}
