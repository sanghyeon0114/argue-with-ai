package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import com.p4c.arguewithai.utils.Logger

data class PassiveDetectionResult(
    val screen: InstagramScreen,
    val screenSinceMs: Long,
    val passiveSinceMs: Long,
    val app: SocialMediaApp,
    val isPassive: Boolean
)

class DetectionScreen {
    private var lastScreen: InstagramScreen = InstagramScreen.NONE
    private var lastScreenSinceMs: Long = 0L

    private var passiveSinceMs: Long = 0L
    private var isPassiveActive: Boolean = false

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
            Logger.d("$lastScreen -> $screen : ${nowMs - lastScreenSinceMs}")
            lastScreen = screen
            lastScreenSinceMs = nowMs
        }
        return screen
    }

    fun detectPassiveApp(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult {
        val screen = detectScreen(root, nowMs)
        var app: SocialMediaApp = SocialMediaApp.resolve(pkg)
        val isPassive = screen in passiveScreen
                || SocialMediaApp.resolve(pkg) == SocialMediaApp.INTERVENTION
                || SocialMediaApp.resolve(pkg) == SocialMediaApp.KEYBOARD
                || SocialMediaApp.resolve(pkg) == SocialMediaApp.SYSTEM
        if (isPassive) {
            if (!isPassiveActive) {
                passiveSinceMs = nowMs
                isPassiveActive = true
            }
        } else {
            isPassiveActive = false
        }
        app =  if (isPassive) SocialMediaApp.PASSIVE_INSTAGRAM else app
        val resultPassiveSinceMs = if (isPassive) passiveSinceMs else nowMs

        return PassiveDetectionResult(
            screen = screen,
            screenSinceMs = lastScreenSinceMs,
            passiveSinceMs = resultPassiveSinceMs,
            app = app,
            isPassive = isPassive
        )
    }
}