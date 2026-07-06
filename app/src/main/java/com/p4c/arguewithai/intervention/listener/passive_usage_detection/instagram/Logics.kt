package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.ShortFormApp
import com.p4c.arguewithai.utils.Logger

object InstagramLogics {
    private val INSTAGRAM_PKG = ShortFormApp.INSTAGRAM.pkg
    private var lastScreen: AppScreen? = null
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, windowList: List<AccessibilityWindowInfo>?, onScreenChanged: ((String) -> Unit)?): Boolean {
        return when (pkg) {
            INSTAGRAM_PKG -> {
                val screen = detectInstagramScreen(pkg, root, onScreenChanged)
                screen == InstagramScreen.FEED || screen == InstagramScreen.REELS || screen == InstagramScreen.SEARCH
            }
            else -> false
        }
    }

    private fun detectInstagramScreen(
        pkg: String?,
        root: AccessibilityNodeInfo,
        onScreenChanged: ((String) -> Unit)?
    ): AppScreen? {
        return when (pkg) {
            INSTAGRAM_PKG -> {
                val cached = lastScreen
                if (cached != null && isStillOnScreen(cached, root)) {
                    return cached
                }
                val screen: AppScreen? = if (isInstagramHomeScreen(root)) {
                    InstagramScreen.FEED
                } else if (isInstagramFeedMenuScreen(root)) {
                    InstagramScreen.FEED_MENU
                } else if (isInstagramWebviewMenuScreen(root)) {
                    InstagramScreen.FEED_WEB_VIEW
                } else if(isInstagramNotificationScreen(root)) {
                    InstagramScreen.NOTIFICATION
                } else if (isInstagramReplyMenuScreen(root)) {
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
                } else if (isInstagramSubscriberListScreen(root)) {
                    InstagramScreen.MY_SUBSCRIBE_LIST
                } else if (isInstagramOtherProfileScreen(root)) {
                    InstagramScreen.OTHER_PROFILE
                } else if (isInstagramOtherSubscribeListScreen(root)) {
                    InstagramScreen.OTHER_SUBSCRIBE_LIST
                } else if(isInstagramStoryScreen(root)) {
                    InstagramScreen.STORY
                } else {
                    null
                }
                if (screen != lastScreen) {
                    val label = screen?.toString() ?: "NONE"
                    Logger.d(label)
                    if(onScreenChanged != null) {
                        onScreenChanged(label)
                    }
                    lastScreen = screen
                }
                screen
            }
            else -> null
        }
    }
    private fun isStillOnScreen(screen: AppScreen, root: AccessibilityNodeInfo): Boolean {
        return when (screen) {
            InstagramScreen.FEED -> isInstagramHomeScreen(root)
            InstagramScreen.FEED_MENU -> isInstagramFeedMenuScreen(root)
            InstagramScreen.FEED_WEB_VIEW -> isInstagramWebviewMenuScreen(root)
            InstagramScreen.NOTIFICATION -> isInstagramNotificationScreen(root)
            InstagramScreen.REPLY -> isInstagramReplyMenuScreen(root)
            InstagramScreen.REELS -> isInstagramReelsScreen(root)
            InstagramScreen.REELS_MENU -> isInstagramReelsMenuScreen(root)
            InstagramScreen.REELS_AUDIO_MENU -> isInstagramReelsAudioMenuScreen(root)
            InstagramScreen.DM -> isInstagramDirectScreen(root)
            InstagramScreen.SEARCH -> isInstagramSearchScreen(root)
            InstagramScreen.MY_PROFILE -> isInstagramProfileScreen(root)
            InstagramScreen.MY_SUBSCRIBE_LIST -> isInstagramSubscriberListScreen(root)
            InstagramScreen.OTHER_PROFILE -> !isInstagramProfileScreen(root) && isInstagramOtherProfileScreen(root)
            InstagramScreen.OTHER_SUBSCRIBE_LIST -> isInstagramOtherSubscribeListScreen(root)
            InstagramScreen.STORY -> isInstagramStoryScreen(root)
            else -> false
        }
    }

    fun isInstagramHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return hasFeedActionButton(root) || isInstagramFollowingFeedScreen(root) || isInstagramBookmarkFeedScreen(root)
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

    private fun hasVisibleNodeById(root: AccessibilityNodeInfo, idSuffix: String): Boolean {
        val fullId = "$INSTAGRAM_PKG:id/$idSuffix"
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
        val labels = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PKG:id/context_menu_item_label")
            ?.filter { it.isVisibleToUser } ?: return false
        val targets = setOf("팔로잉", "즐겨찾기")
        val foundTexts = labels.mapNotNull { it.text?.toString() }.toSet()
        return foundTexts.containsAll(targets)
    }
    fun isInstagramWebviewMenuScreen(root: AccessibilityNodeInfo?): Boolean {
        val labelId = "$INSTAGRAM_PKG:id/title_textview"
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
    private fun isInstagramFollowingFeedScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "$INSTAGRAM_PKG:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "팔로잉" }
    }
    private fun isInstagramBookmarkFeedScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "$INSTAGRAM_PKG:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "즐겨찾기" }
    }

    // ############## Notification Screen ##############
    private fun isInstagramNotificationScreen(root: AccessibilityNodeInfo): Boolean {
        val titleId = "$INSTAGRAM_PKG:id/action_bar_title"
        val nodes = root.findAccessibilityNodeInfosByViewId(titleId) ?: return false
        return nodes.any { it.isVisibleToUser && it.text?.toString() == "알림" }
    }

    fun isInstagramReplyMenuScreen(root: AccessibilityNodeInfo?): Boolean {
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
        val labelId = "$INSTAGRAM_PKG:id/context_menu_item_label"
        val targets = setOf("팔로잉")
        val labelNodes = root?.findAccessibilityNodeInfosByViewId(labelId) ?: return false
        return labelNodes.any { node ->
            node.isVisibleToUser && node.text?.toString() in targets
        }
    }

    fun isInstagramReelsAudioMenuScreen(root: AccessibilityNodeInfo?): Boolean {
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
        val fullId = "$INSTAGRAM_PKG:id/$tabIdSuffix"
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

    // ############## PROFILE Screen ##############
    fun isInstagramSubscriberListScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val followListTabId = "$INSTAGRAM_PKG:id/unified_follow_list_tab_layout"
        val titleId = "$INSTAGRAM_PKG:id/title"

        val hasFollowListTab = root.findAccessibilityNodeInfosByViewId(followListTabId)
            .any { it.isVisibleToUser }

        val hasSubscriberTitle = root.findAccessibilityNodeInfosByViewId(titleId)
            .any { it.isVisibleToUser && it.text?.toString()?.contains("구독") == true }

        return hasFollowListTab && hasSubscriberTitle
    }

    fun isInstagramOtherProfileScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val containerId = "$INSTAGRAM_PKG:id/profile_header_container"

        val hasProfileHeader = root.findAccessibilityNodeInfosByViewId(containerId)
            .any { it.isVisibleToUser }

        return hasProfileHeader
    }
    fun isInstagramOtherSubscribeListScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val followListTabId = "$INSTAGRAM_PKG:id/unified_follow_list_tab_layout"
        val titleId = "$INSTAGRAM_PKG:id/title"

        val recommendTargets = setOf("추천")

        val hasFollowListTab = root.findAccessibilityNodeInfosByViewId(followListTabId)
            .any { it.isVisibleToUser }
        val hasRecommendTitle = root.findAccessibilityNodeInfosByViewId(titleId)
            .any { it.isVisibleToUser && it.text?.toString() in recommendTargets }

        return hasFollowListTab && hasRecommendTitle
    }


    // ############## Story Screen ##############

    private fun isInstagramStoryScreen(root: AccessibilityNodeInfo): Boolean {
        val viewerRoot = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/reel_viewer_root"
        )
        val progressBar = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/reel_viewer_progress_bar"
        )

        val hasViewerRoot = viewerRoot.isNotEmpty()
        val hasProgressBar = progressBar.isNotEmpty()

        viewerRoot.forEach { it.recycle() }
        progressBar.forEach { it.recycle() }

        return hasViewerRoot && hasProgressBar
    }
}