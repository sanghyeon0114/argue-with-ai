package com.p4c.arguewithai.intervention.listener.instagram.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.InstagramScreen

object InstagramLogics {
    val INSTAGRAM_PKG = SocialMediaApp.INSTAGRAM.pkg

    fun resolveScreen(root: AccessibilityNodeInfo): InstagramScreen {
        return when {
            Feed.isFeedScreen(root) -> InstagramScreen.FEED
            Feed.isFeedMenuScreen(root) -> InstagramScreen.FEED_MENU
            Feed.isWebviewMenuScreen(root) -> InstagramScreen.FEED_WEB_VIEW
            Notification.isNotificationScreen(root) -> InstagramScreen.NOTIFICATION
            ReplyMenu.isReplyMenuScreen(root) -> InstagramScreen.REPLY
            Reels.isReelsScreen(root) -> InstagramScreen.REELS
            Reels.isReelsMenuScreen(root) -> InstagramScreen.REELS_MENU
            Reels.isReelsAudioMenuScreen(root) -> InstagramScreen.REELS_AUDIO_MENU
            Direct.isDirectScreen(root) -> InstagramScreen.DM
            Search.isSearchScreen(root) -> InstagramScreen.SEARCH
            Profile.isProfileScreen(root) -> InstagramScreen.MY_PROFILE
            Profile.isSubscriberListScreen(root) -> InstagramScreen.MY_SUBSCRIBE_LIST
            Profile.isOtherProfileScreen(root) -> InstagramScreen.OTHER_PROFILE
            Profile.isOtherSubscribeListScreen(root) -> InstagramScreen.OTHER_SUBSCRIBE_LIST
            Story.isStoryScreen(root) -> InstagramScreen.STORY
            else -> InstagramScreen.NONE
        }
    }
    fun isStillOnScreen(screen: InstagramScreen, root: AccessibilityNodeInfo): Boolean {
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