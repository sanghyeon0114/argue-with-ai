package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.InstagramLogics.INSTAGRAM_PKG

object Notification {
    fun isNotificationScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "${INSTAGRAM_PKG}:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "알림" }
    }
}