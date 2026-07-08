package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Feed {
    fun isFeedScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return hasFeedActionButton(root) || isFollowingFeedScreen(root) || isBookmarkFeedScreen(root)
    }

    private fun hasFeedActionButton(root: AccessibilityNodeInfo): Boolean {
        val buttonIds = listOf(
            "row_feed_button_like",
            "row_feed_button_comment",
            "row_feed_button_share",
            "row_feed_button_save",
            "row_feed_photo_profile_imageview",
            "row_feed_photo_profile_name"
        )
        return buttonIds.any { hasVisibleNodeById(root, it) }
    }
    fun isFeedMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val labels = root.findAccessibilityNodeInfosByViewId("${InstagramLogics.INSTAGRAM_PKG}:id/context_menu_item_label")
            ?.filter { it.isVisibleToUser } ?: return false
        val targets = setOf("팔로잉", "즐겨찾기")
        val foundTexts = labels.mapNotNull { it.text?.toString() }.toSet()
        return foundTexts.containsAll(targets)
    }
    fun isWebviewMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "${InstagramLogics.INSTAGRAM_PKG}:id/title_textview"
        val targets = setOf(
            "웹사이트 신고",
            "새로 고침",
            "삼성 브라우저에서 열기",
            "링크 복사",
            "공유 수단...",
            "Direct를 통해 공유",
            "브라우저 설정",
            "개인정보처리방침"
        )
        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        val foundTexts = labelNodes
            .filter { it.isVisibleToUser }
            .mapNotNull { it.text?.toString() }
            .toSet()

        return foundTexts.containsAll(targets)
    }
    private fun isFollowingFeedScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "${InstagramLogics.INSTAGRAM_PKG}:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "팔로잉" }
    }
    private fun isBookmarkFeedScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "${InstagramLogics.INSTAGRAM_PKG}:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "즐겨찾기" }
    }
    fun hasVisibleNodeById(root: AccessibilityNodeInfo, idSuffix: String): Boolean {
        val fullId = "${InstagramLogics.INSTAGRAM_PKG}:id/$idSuffix"
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }
}