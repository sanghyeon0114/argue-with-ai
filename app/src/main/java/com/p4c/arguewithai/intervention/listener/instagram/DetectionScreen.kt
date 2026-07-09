package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics

data class PassiveDetectionResult(
    val screen: InstagramScreen,
    val screenSinceMs: Long,
    val app: SocialMediaApp
)

class DetectionScreen {
    private var lastScreen: InstagramScreen = InstagramScreen.NONE
    private var lastScreenSinceMs: Long = 0L

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

    fun detectScreen(root: AccessibilityNodeInfo, nowMs: Long): InstagramScreen {
        val cached = lastScreen
        if (cached != InstagramScreen.NONE && InstagramLogics.isStillOnScreen(cached, root)) {
            return cached
        }

        val screen = InstagramLogics.resolveScreen(root)
        if (screen != lastScreen) {
            lastScreen = screen
            lastScreenSinceMs = nowMs
        }
        return screen
    }

    fun detectPassiveApp(pkg: String?, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult {
        if (pkg != InstagramLogics.INSTAGRAM_PKG) {
            return PassiveDetectionResult(screen = InstagramScreen.NONE, screenSinceMs = nowMs, app = SocialMediaApp.NONE)
        }

        val screen = detectScreen(root, nowMs)
        val app = if (screen in passiveScreen) SocialMediaApp.INSTAGRAM else SocialMediaApp.NONE

        return PassiveDetectionResult(screen = screen, screenSinceMs = lastScreenSinceMs, app = app)
    }
}