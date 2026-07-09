package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import com.p4c.arguewithai.utils.Logger

class DetectionScreen {
    private var lastScreen: InstagramScreen? = null
    private val passiveScreen = setOf(
        InstagramScreen.FEED,
        InstagramScreen.FEED_MENU,
        InstagramScreen.FEED_WEB_VIEW,
        InstagramScreen.NOTIFICATION,
        InstagramScreen.REELS,
        InstagramScreen.REELS_MENU,
        InstagramScreen.REELS_AUDIO_MENU,
        InstagramScreen.SEARCH,
        InstagramScreen.MY_PROFILE,
        InstagramScreen.MY_SUBSCRIBE_LIST,
        InstagramScreen.OTHER_PROFILE,
        InstagramScreen.OTHER_SUBSCRIBE_LIST,
        InstagramScreen.REPLY,
        InstagramScreen.STORY
    )

    fun detectScreen(root: AccessibilityNodeInfo, onScreenChanged: ((String) -> Unit)? = null): InstagramScreen? {
        val cached = lastScreen
        if (cached != null && InstagramLogics.isStillOnScreen(cached, root)) {
            return cached
        }

        val screen = InstagramLogics.resolveScreen(root)
        if (screen != lastScreen) {
            val label = screen?.toString() ?: "NONE"
            Logger.d(label)
            onScreenChanged?.invoke(label)
            lastScreen = screen
        }
        return screen
    }

    fun detectPassiveApp(pkg: String?, root: AccessibilityNodeInfo, onScreenChanged: ((String) -> Unit)? = null): SocialMediaApp? {
        if (pkg != InstagramLogics.INSTAGRAM_PKG) return null
        val screen = detectScreen(root, onScreenChanged)
        return if (screen in passiveScreen) {
            SocialMediaApp.INSTAGRAM
        } else {
            null
        }
    }

    fun reset() {
        if (lastScreen != null) {
            Logger.d("Tracker reset (was: $lastScreen)")
        }
        lastScreen = null
    }
}