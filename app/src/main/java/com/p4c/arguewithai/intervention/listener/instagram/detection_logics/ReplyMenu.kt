package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object ReplyMenu {
    fun isReplyMenuScreen(root: AccessibilityNodeInfo): Boolean {
        val labelId = "${InstagramLogics.INSTAGRAM_PKG}:id/context_menu_item_label"
        val itemId = "${InstagramLogics.INSTAGRAM_PKG}:id/context_menu_item"

        val labelTargets = setOf("릴스로 답글 달기")
        val itemTargets = setOf("공유하기", "신고", "차단")

        val labelNodes = root.findAccessibilityNodeInfosByViewId(labelId)
        val itemNodes = root.findAccessibilityNodeInfosByViewId(itemId)

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