package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.InstagramLogics.INSTAGRAM_PKG

object ReplyMenu {
    fun isReplyMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$INSTAGRAM_PKG:id/context_menu_item_label"
        val itemId = "$INSTAGRAM_PKG:id/context_menu_item"

        val labelTargets = setOf("릴스로 답글 달기")
        val itemTargets = setOf("공유하기", "신고", "차단")

        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        val itemNodes = root.findAccessibilityNodeInfosByViewId(itemId) ?: return false

        val hasLabel = labelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in labelTargets
        }
        val foundItemDescs = itemNodes
            .filter { it.isVisibleToUser }
            .mapNotNull { it.contentDescription?.toString() }
            .toSet()

        val hasAllItems = foundItemDescs.containsAll(itemTargets)

        return hasLabel && hasAllItems
    }
}