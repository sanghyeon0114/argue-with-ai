package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.ShortFormApp
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Feed
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Notification
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.ReplyMenu
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Reels
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Direct
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Search
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Profile
import com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram.detection_logics.Story
import com.p4c.arguewithai.utils.Logger

object InstagramLogics {
    val INSTAGRAM_PKG = ShortFormApp.INSTAGRAM.pkg
    private var lastScreen: InstagramScreen? = null
    fun detectApp(pkg: String?, root: AccessibilityNodeInfo, onScreenChanged: ((String) -> Unit)?): Boolean {
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
    ): InstagramScreen? {
        return when (pkg) {
            INSTAGRAM_PKG -> {
                val cached = lastScreen
                if (cached != null && isStillOnScreen(cached, root)) {
                    return cached
                }
                val screen: InstagramScreen? = if (Feed.isFeedScreen(root)) {
                    InstagramScreen.FEED
                } else if (Feed.isFeedMenuScreen(root)) {
                    InstagramScreen.FEED_MENU
                } else if (Feed.isWebviewMenuScreen(root)) {
                    InstagramScreen.FEED_WEB_VIEW
                } else if(Notification.isNotificationScreen(root)) {
                    InstagramScreen.NOTIFICATION
                } else if (ReplyMenu.isReplyMenuScreen(root)) {
                    InstagramScreen.REPLY
                } else if (Reels.isReelsScreen(root)) {
                    InstagramScreen.REELS
                } else if (Reels.isReelsMenuScreen(root)) {
                    InstagramScreen.REELS_MENU
                } else if (Reels.isReelsAudioMenuScreen(root)) {
                    InstagramScreen.REELS_AUDIO_MENU
                } else if (Direct.isDirectScreen(root)) {
                    InstagramScreen.DM
                } else if (Search.isSearchScreen(root)) {
                    InstagramScreen.SEARCH
                } else if (Profile.isProfileScreen(root)) {
                    InstagramScreen.MY_PROFILE
                } else if (Profile.isSubscriberListScreen(root)) {
                    InstagramScreen.MY_SUBSCRIBE_LIST
                } else if (Profile.isOtherProfileScreen(root)) {
                    InstagramScreen.OTHER_PROFILE
                } else if (Profile.isOtherSubscribeListScreen(root)) {
                    InstagramScreen.OTHER_SUBSCRIBE_LIST
                } else if(Story.isStoryScreen(root)) {
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
    private fun isStillOnScreen(screen: InstagramScreen, root: AccessibilityNodeInfo): Boolean {
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
        }
    }
}