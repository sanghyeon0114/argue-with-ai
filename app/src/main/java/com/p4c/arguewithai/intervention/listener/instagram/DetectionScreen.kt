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
    private var pendingNoneSinceMs: Long? = null

    private var passiveSinceMs: Long = 0L
    private var isPassiveActive: Boolean = false
    private var passiveHitStreak: Int = 0
    private var noneHitStreak: Int = 0
    companion object {
        private const val NONE_GRACE_PERIOD_MS = 100L
        private const val PASSIVE_ENTER_CONFIRM_COUNT = 5
        private const val PASSIVE_EXIT_CONFIRM_COUNT = 5
    }

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
            pendingNoneSinceMs = null
            return cached
        }

        val screen = InstagramLogics.resolveScreen(root)

        if (screen == InstagramScreen.NONE && lastScreen != InstagramScreen.NONE) {
            val pendingSince = pendingNoneSinceMs
            if (pendingSince == null) {
                pendingNoneSinceMs = nowMs
                return lastScreen
            }
            if (nowMs - pendingSince < NONE_GRACE_PERIOD_MS) {
                return lastScreen
            }
        } else {
            pendingNoneSinceMs = null
        }

        if (screen != lastScreen) {
            val elapsed = if (lastScreen == InstagramScreen.NONE) 0L else nowMs - lastScreenSinceMs
            Logger.d("$lastScreen -> $screen : $elapsed")
            lastScreen = screen
            lastScreenSinceMs = nowMs
        }
        return screen
    }

    fun detectPassiveApp(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult {
        val screen = detectScreen(root, nowMs)
        var app: SocialMediaApp = SocialMediaApp.resolve(pkg)
        val rawPassive = if (!isPassiveActive) {
            screen in passiveScreen
        } else {
            screen in passiveScreen || SocialMediaApp.resolve(pkg) == SocialMediaApp.INTERVENTION || SocialMediaApp.resolve(pkg) == SocialMediaApp.KEYBOARD || SocialMediaApp.resolve(pkg) == SocialMediaApp.SYSTEM
        }

        if (rawPassive) {
            passiveHitStreak++
            noneHitStreak = 0
        } else {
            noneHitStreak++
            passiveHitStreak = 0
        }

        if (!isPassiveActive && passiveHitStreak >= PASSIVE_ENTER_CONFIRM_COUNT) {
            isPassiveActive = true
            passiveSinceMs = nowMs
        } else if (isPassiveActive && noneHitStreak >= PASSIVE_EXIT_CONFIRM_COUNT) {
            isPassiveActive = false
        }

        app = if (isPassiveActive) SocialMediaApp.PASSIVE_INSTAGRAM else app
        val resultPassiveSinceMs = if (isPassiveActive) passiveSinceMs else nowMs

        return PassiveDetectionResult(
            screen = screen,
            screenSinceMs = lastScreenSinceMs,
            passiveSinceMs = resultPassiveSinceMs,
            app = app,
            isPassive = isPassiveActive
        )
    }
}