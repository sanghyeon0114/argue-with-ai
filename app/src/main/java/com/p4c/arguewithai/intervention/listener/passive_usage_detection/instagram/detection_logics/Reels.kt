package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.InstagramLogics.INSTAGRAM_PKG

object Reels {
    fun isReelsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isReelsContainer(root)
    }

    private fun isReelsContainer(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        return hasVisibleNodeById(root, "root_clips_layout") ||
                hasVisibleNodeById(root, "clips_linear_layout_container") ||
                hasVisibleNodeById(root, "clips_viewer_container") ||
                hasVisibleNodeById(root, "clips_swipe_refresh_container") ||
                hasVisibleNodeById(root, "clips_viewer_view_pager")
    }

    fun isReelsMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$INSTAGRAM_PKG:id/context_menu_item_label"
        val targets = setOf("팔로잉")
        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        return labelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in targets
        }
    }

    fun isReelsAudioMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$INSTAGRAM_PKG:id/context_menu_item_label"
        val subLabelId = "$INSTAGRAM_PKG:id/context_menu_item_sub_label"
        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        val subLabelNodes = root.findAccessibilityNodeInfosByViewId(subLabelId) ?: return false

        val labelTargets = setOf("리믹스 및 시퀀스")
        val subLabelTargets = setOf("오디오")
        val hasLabel = labelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in labelTargets
        }
        val hasSubLabel = subLabelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in subLabelTargets
        }

        return hasLabel && hasSubLabel
    }

    fun hasVisibleNodeById(root: AccessibilityNodeInfo, idSuffix: String): Boolean {
        val fullId = "$INSTAGRAM_PKG:id/$idSuffix"
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
}