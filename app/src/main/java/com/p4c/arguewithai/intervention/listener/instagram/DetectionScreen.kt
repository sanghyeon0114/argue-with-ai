package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import com.p4c.arguewithai.utils.Logger

data class PassiveDetectionResult(
    val screen: InstagramScreen?,
    val screenElapsedMs: Long,
    val passiveElapsedMs: Long,
    val totalPassiveMs: Long,
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

    private var totalPassiveMs: Long = 0L
    private var lastTickMs: Long? = null
    companion object {
        private const val PASSIVE_ENTER_CONFIRM_COUNT = 5
        private const val PASSIVE_EXIT_CONFIRM_COUNT = 5
        private val PASSIVE_TOLERATED_APPS = setOf(
            SocialMediaApp.INTERVENTION,
            SocialMediaApp.KEYBOARD,
            SocialMediaApp.SYSTEM
        )
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

    private fun getScreen(pkg: String, root: AccessibilityNodeInfo, window: () -> AccessibilityWindowInfo?, nowMs: Long): InstagramScreen? {
        if (pkg != InstagramLogics.INSTAGRAM_PKG) {
            return null
        }

        val cached = lastScreen
        if (cached != InstagramScreen.NONE && InstagramLogics.isCurrentScreen(cached, root)) {
            pendingNoneSinceMs = null
            return cached
        }

        val screen = InstagramLogics.getScreenName(root)

        // 키보드 처리 조건문
        if (screen == InstagramScreen.NONE && lastScreen != InstagramScreen.NONE &&
            window()?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        ) {
            return lastScreen
        }

        if (screen != lastScreen) {
            val elapsed = if (lastScreen == InstagramScreen.NONE) 0L else nowMs - lastScreenSinceMs
            Logger.d("-------------- $lastScreen -> $screen : $elapsed --------------")
            lastScreen = screen
            lastScreenSinceMs = nowMs
        }
        return screen
    }
    fun getScreenInformation(pkg: String, root: AccessibilityNodeInfo, window: () -> AccessibilityWindowInfo?, nowMs: Long): PassiveDetectionResult {
        val screen = getScreen(pkg, root, window, nowMs)
        val currentApp: SocialMediaApp = SocialMediaApp.find(pkg)
        val isToleratedWhilePassive = isPassiveActive &&
            (currentApp in PASSIVE_TOLERATED_APPS || window()?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        val isPassiveScreen = (screen != null && screen in passiveScreen) || isToleratedWhilePassive
        val wasActive = isPassiveActive

        if (isPassiveScreen) {
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
        if (!isPassiveActive) {
            passiveSinceMs = nowMs
        }

        val tickDelta = lastTickMs?.let { nowMs - it } ?: 0L
        lastTickMs = nowMs
        if (isPassiveActive) {
            totalPassiveMs += tickDelta
        }
        if (wasActive && !isPassiveActive) {
            Logger.d("-------------- passive 종료, 누적 passive 시간 = ${totalPassiveMs}ms --------------")
        }

        return PassiveDetectionResult(
            screen = screen,
            screenElapsedMs = nowMs - lastScreenSinceMs,
            passiveElapsedMs = nowMs - passiveSinceMs,
            totalPassiveMs = totalPassiveMs,
            app = currentApp,
            isPassive = isPassiveActive
        )
    }
}