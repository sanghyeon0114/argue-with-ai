package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import kotlin.Boolean

data class ScreenData(
    var isInstagram: Boolean,
    val screen: InstagramScreen,
    val isPassive: Boolean
)

data class PassiveDetectionResult(
    val app: SocialMediaApp,
    val screen: InstagramScreen?,
    val screenMs: Long,
    val passiveMs: Long,
    val isPassive: Boolean
)

class DetectionScreen {
    private var lastScreen: InstagramScreen = InstagramScreen.NONE
    private var currentScreen: InstagramScreen = InstagramScreen.NONE
    private var currentScreenSinceMs: Long = 0L
    private var isPassive: Boolean = false

    private var isInstagramActive: Boolean = false
    private var instagramHitStreak: Int = 0
    private var notInstagramHitStreak: Int = 0
    private var passiveSinceMs: Long = 0L

    companion object {
        private const val INSTAGRAM_ENTER_CONFIRM_COUNT = 10
        private const val INSTAGRAM_EXIT_CONFIRM_COUNT = 10
        private val PASSIVE_SCREEN = setOf(
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
        private fun isPassive(screen: InstagramScreen): Boolean {
            return screen in PASSIVE_SCREEN
        }
    }

    private fun updateInstagramActive(isInstagramPkg: Boolean) {
        if (isInstagramPkg) {
            instagramHitStreak++
            notInstagramHitStreak = 0
        } else {
            notInstagramHitStreak++
            instagramHitStreak = 0
        }
        if (!isInstagramActive && instagramHitStreak >= INSTAGRAM_ENTER_CONFIRM_COUNT) {
            isInstagramActive = true
        } else if (isInstagramActive && notInstagramHitStreak >= INSTAGRAM_EXIT_CONFIRM_COUNT) {
            isInstagramActive = false
        }
    }

    fun getScreen(pkg: String, root: AccessibilityNodeInfo): ScreenData {
        val isInstagramPkg = pkg == InstagramLogics.INSTAGRAM_PKG
        updateInstagramActive(isInstagramPkg)

        if (!isInstagramPkg) {
            return ScreenData(
                isInstagram = isInstagramActive,
                screen = InstagramScreen.NONE,
                isPassive = false
            )
        }
        if(InstagramLogics.isCurrentScreen(lastScreen, root)) {
            return ScreenData(
                isInstagram = isInstagramActive,
                screen = lastScreen,
                isPassive = isPassive(lastScreen)
            )
        }
        val screen = InstagramLogics.getScreenName(root)
        if(screen == InstagramScreen.NONE) {
            return ScreenData(
                isInstagram = isInstagramActive,
                screen = InstagramScreen.NONE,
                isPassive = isPassive(InstagramScreen.NONE)
            )
        }
        if(screen != lastScreen) {
            lastScreen = screen
        }
        return ScreenData(
            isInstagram = isInstagramActive,
            screen = lastScreen,
            isPassive = isPassive(lastScreen)
        )
    }

    /*
    * currentApp pkg가 Instagram이 아니더라도, currentScreen를 보장한다.
    * 만약 pkg가 Instagram이 아니라면, null을 반환한다.
    * */
    fun getScreenInformation(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult? {
        val data: ScreenData = getScreen(pkg, root)
        val isInstagram = data.isInstagram

        if(!isInstagram) {
            return null
        }
        // ------------------ isInstagram = true ------------------
        val currentApp = SocialMediaApp.find(pkg)
        val screen: InstagramScreen = data.screen
        val isP: Boolean = data.isPassive

        if(currentApp == SocialMediaApp.INSTAGRAM) {
            if (screen != currentScreen) {
                currentScreen = screen
                currentScreenSinceMs = nowMs
            }
            if (isP != isPassive) {
                isPassive = isP
                passiveSinceMs = nowMs
            }
        }

        return PassiveDetectionResult(
            app = currentApp,
            screen = currentScreen,
            screenMs = nowMs - currentScreenSinceMs,
            passiveMs = if (isPassive) nowMs - passiveSinceMs else 0,
            isPassive = isPassive
        )
    }
}