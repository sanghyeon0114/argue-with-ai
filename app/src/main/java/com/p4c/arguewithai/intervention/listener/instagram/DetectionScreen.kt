package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import com.p4c.arguewithai.utils.Logger

data class PassiveDetectionResult(
    val screen: InstagramScreen?,
    val app: SocialMediaApp?
)

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

    fun detectScreen(root: AccessibilityNodeInfo): InstagramScreen? {
        val cached = lastScreen
        if (cached != null && InstagramLogics.isStillOnScreen(cached, root)) {
            return cached
        }

        val screen = InstagramLogics.resolveScreen(root)
        if (screen != lastScreen) {
            val label = screen?.toString() ?: "NONE"
            Logger.d(label)
            lastScreen = screen
        }
        return screen
    }

    fun detectPassiveApp(pkg: String?, root: AccessibilityNodeInfo): PassiveDetectionResult {
        if (pkg != InstagramLogics.INSTAGRAM_PKG) {
            return PassiveDetectionResult(screen = null, app = null)
        }

        val screen = detectScreen(root)
        val app = if (screen in passiveScreen) SocialMediaApp.INSTAGRAM else null

        return PassiveDetectionResult(screen = screen, app = app)
    }
}