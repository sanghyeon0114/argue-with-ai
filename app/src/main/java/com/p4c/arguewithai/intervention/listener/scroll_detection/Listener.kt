package com.p4c.arguewithai.intervention.listener.scroll_detection

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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
        windowList: List<AccessibilityWindowInfo>? = null,
        nowMs: Long = System.currentTimeMillis(),
        onScreenChanged: (String) -> Unit = {}
    ) {
        if (event == null || root == null) {
            maybeExitOnInvisibility(nowMs)
            return
        }

        val pkg = event.packageName?.toString()
        val detected: ShortFormApp? = Logics.detectApp(pkg, root, windowList, onScreenChanged)

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
}