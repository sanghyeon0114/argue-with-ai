package com.p4c.arguewithai.intervention.listener.scroll

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.utils.Logger

object Logics {
    private const val IG_PKG = "com.instagram.android"
    private var lastScreen: AppScreen? = null
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, windowList: List<AccessibilityWindowInfo>?): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> if(detectInstagramScreen(pkg, root) == InstagramScreen.FEED || detectInstagramScreen(pkg, root) == InstagramScreen.REELS || detectInstagramScreen(pkg, root) == InstagramScreen.SEARCH) ShortFormApp.INSTAGRAM else null
            else -> null
        }
    }
    private fun detectInstagramScreen(pkg: String?, root: AccessibilityNodeInfo): AppScreen? {
        return when (pkg) {
            ShortFormApp.INSTAGRAM.pkg -> {
                val screen: AppScreen? = if (isInstagramHomeScreen(root)) {
                    InstagramScreen.FEED
                } else if (isInstagramFeedMenuScreen(root)) {
                    InstagramScreen.FEED_MENU
                } else if(isInstagramWebviewMenuScreen(root)) {
                    InstagramScreen.FEED_WEB_VIEW
                } else if(isInstagramReplyMenuScreen(root)) {
                    InstagramScreen.REPLY
                } else if (isInstagramReelsScreen(root)) {
                    InstagramScreen.REELS
                } else if (isInstagramReelsMenuScreen(root)) {
                    InstagramScreen.REELS_MENU
                } else if (isInstagramReelsAudioMenuScreen(root)) {
                    InstagramScreen.REELS_AUDIO_MENU
                } else if (isInstagramDirectScreen(root)) {
                    InstagramScreen.DM
                } else if (isInstagramSearchScreen(root)) {
                    InstagramScreen.SEARCH
                } else if (isInstagramProfileScreen(root)) {
                    InstagramScreen.MY_PROFILE
                } else {
                    null
                }
                if (screen != lastScreen) {
                    Logger.d(screen?.toString() ?: "NONE")
                    lastScreen = screen
                }
                screen
            }
            else -> null
        }
    }
    fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return hasFeedActionButton(root)
    }
    fun isInstagramReelsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isInstagramReelsContainer(root)
    }
    fun isInstagramDirectScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isInstagramDirectTab(root)
    }
    fun isInstagramSearchScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isInstagramSearchTab(root)
    }
    fun isInstagramProfileScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isInstagramProfileTab(root)
    }

    // ############## Check Sceen Tab ##############
//    private fun isInstagramHomeTab(root: AccessibilityNodeInfo): Boolean {
//        return isTabSelected(root, "feed_tab") || hasVisibleNodeById(root, "title_logo")
//    }
//    private fun isInstagramReelsTab(root: AccessibilityNodeInfo): Boolean {
//        return isTabSelected(root, "clips_tab")
//    }
    private fun isInstagramDirectTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "direct_tab")
    }
    private fun isInstagramSearchTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "search_tab")
    }
    private fun isInstagramProfileTab(root: AccessibilityNodeInfo): Boolean {
        return isTabSelected(root, "profile_tab")
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, tabIdSuffix: String, iconIdSuffixes: List<String> = listOf("tab_icon", "tab_avatar")): Boolean {
        val fullId = "$IG_PKG:id/$tabIdSuffix"
        val tabs = root.findAccessibilityNodeInfosByViewId(fullId) ?.filter { it.isVisibleToUser } ?: return false

        return tabs.any { tab ->
            tab.isSelected ||
                    (0 until tab.childCount).any { i ->
                        val child = tab.getChild(i) ?: return@any false
                        val childId = child.viewIdResourceName ?: ""
                        iconIdSuffixes.any { childId.endsWith(it) } && child.isSelected
                    }
        }
    }
    private fun hasVisibleNodeById(root: AccessibilityNodeInfo, idSuffix: String): Boolean {
        val fullId = "$IG_PKG:id/$idSuffix"
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: return false
        return nodes.any { it.isVisibleToUser }
    }

    // ############## Feed Screen ##############
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
    fun isInstagramFeedMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val labels = root.findAccessibilityNodeInfosByViewId("$IG_PKG:id/context_menu_item_label")
            ?.filter { it.isVisibleToUser } ?: return false
        val targets = setOf("팔로잉", "즐겨찾기")
        val foundTexts = labels.mapNotNull { it.text?.toString() }.toSet()
        return foundTexts.containsAll(targets)
    }
    fun isInstagramWebviewMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$IG_PKG:id/title_textview"
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
    fun isInstagramReplyMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$IG_PKG:id/context_menu_item_label"
        val itemId = "$IG_PKG:id/context_menu_item"

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

    // ############## Reels Screen ##############

    private fun isInstagramReelsContainer(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        return hasVisibleNodeById(root, "root_clips_layout") ||
                hasVisibleNodeById(root, "clips_linear_layout_container") ||
                hasVisibleNodeById(root, "clips_viewer_container") ||
                hasVisibleNodeById(root, "clips_swipe_refresh_container") ||
                hasVisibleNodeById(root, "clips_viewer_view_pager")
    }


    fun isInstagramReelsMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$IG_PKG:id/context_menu_item_label"
        val targets = setOf("팔로잉")
        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        return labelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in targets
        }
    }

    fun isInstagramReelsAudioMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$IG_PKG:id/context_menu_item_label"
        val subLabelId = "$IG_PKG:id/context_menu_item_sub_label"
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
}