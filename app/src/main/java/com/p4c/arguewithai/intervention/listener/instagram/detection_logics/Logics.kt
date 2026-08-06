package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.InstagramScreen

object InstagramLogics {
    val INSTAGRAM_PKG: String = SocialMediaApp.INSTAGRAM.pkg

    fun getScreenName(root: AccessibilityNodeInfo): InstagramScreen {
        val isReels by lazy { Reels.isReelsScreen(root) }
        val isDirect by lazy { Direct.isDirectScreen(root) }
        val isSearch by lazy { Search.isSearchScreen(root) }
        val isProfile by lazy { Profile.isProfileScreen(root) }

        return when {
            Feed.isFeedContentScreen(root) -> InstagramScreen.FEED
            Feed.isFeedMenuScreen(root) -> InstagramScreen.FEED_MENU
            Feed.isWebviewMenuScreen(root) -> InstagramScreen.FEED_WEB_VIEW
            Notification.isNotificationScreen(root) -> InstagramScreen.NOTIFICATION
            ReplyMenu.isReplyMenuScreen(root) -> InstagramScreen.REPLY
            isReels -> InstagramScreen.REELS
            Reels.isReelsMenuScreen(root) -> InstagramScreen.REELS_MENU
            Reels.isReelsAudioMenuScreen(root) -> InstagramScreen.REELS_AUDIO_MENU
            isDirect -> InstagramScreen.DM
            isSearch -> InstagramScreen.SEARCH
            isProfile -> InstagramScreen.MY_PROFILE
            Profile.isSubscriberListScreen(root) -> InstagramScreen.MY_SUBSCRIBE_LIST
            Profile.isOtherProfileScreen(root) -> InstagramScreen.OTHER_PROFILE
            Profile.isOtherSubscribeListScreen(root) -> InstagramScreen.OTHER_SUBSCRIBE_LIST
            Story.isStoryScreen(root) -> InstagramScreen.STORY
            Feed.isMainScreen(root) && !isReels && !isDirect && !isSearch && !isProfile -> InstagramScreen.FEED
            else -> InstagramScreen.NONE
        }
    }
    fun isCurrentScreen(screen: InstagramScreen, root: AccessibilityNodeInfo): Boolean {
        return when (screen) {
            InstagramScreen.FEED -> Feed.isFeedScreen(root)
            InstagramScreen.FEED_MENU -> Feed.isFeedMenuScreen(root)
            InstagramScreen.FEED_WEB_VIEW -> Feed.isWebviewMenuScreen(root)
            InstagramScreen.NOTIFICATION -> Notification.isNotificationScreen(root)
            InstagramScreen.REPLY -> ReplyMenu.isReplyMenuScreen(root)
            InstagramScreen.REELS -> Reels.isReelsScreen(root)
            InstagramScreen.REELS_MENU -> Reels.isReelsMenuScreen(root)
            InstagramScreen.REELS_AUDIO_MENU -> Reels.isReelsAudioMenuScreen(root)
            InstagramScreen.DM -> Direct.isDirectScreen(root)
            InstagramScreen.SEARCH -> Search.isSearchScreen(root)
            InstagramScreen.MY_PROFILE -> Profile.isProfileScreen(root)
            InstagramScreen.MY_SUBSCRIBE_LIST -> Profile.isSubscriberListScreen(root)
            InstagramScreen.OTHER_PROFILE -> !Profile.isProfileScreen(root) && Profile.isOtherProfileScreen(root)
            InstagramScreen.OTHER_SUBSCRIBE_LIST -> Profile.isOtherSubscribeListScreen(root)
            InstagramScreen.STORY -> Story.isStoryScreen(root)
            InstagramScreen.NONE -> false
        }
    }
}