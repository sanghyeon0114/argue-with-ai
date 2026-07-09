package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Notification {
    fun isNotificationScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "${InstagramLogics.INSTAGRAM_PKG}:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId)
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "알림" }
    }
}