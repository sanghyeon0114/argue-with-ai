package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import kotlin.Boolean

data class ScreenData(
    var isInstagram: Boolean,
    val screen: InstagramScreen,
    val isPassive: Boolean
)

class InstagramTracker {
    private var lastScreen: InstagramScreen = InstagramScreen.NONE
    private var currentScreen: InstagramScreen = InstagramScreen.NONE
    private var currentScreenSinceMs: Long = 0L
    private var isPassive: Boolean = false

    private var isInstagramActive: Boolean = false
    private var instagramHitStreak: Int = 0
    private var notInstagramHitStreak: Int = 0
    private var passiveSinceMs: Long = 0L
    private var noneScreenStreak: Int = 0
    private var lastKnownApp: SocialMediaApp = SocialMediaApp.INSTAGRAM


    companion object {
        private const val INSTAGRAM_ENTER_CONFIRM_COUNT = 10
        private const val INSTAGRAM_EXIT_CONFIRM_COUNT = 10
        private const val NONE_SCREEN_CONFIRM_COUNT = 3
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
            InstagramScreen.STORY,
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
        val socialApp = SocialMediaApp.find(pkg)
        val isOwnAppPkg = socialApp == SocialMediaApp.INTERVENTION
        val isSystemPkg = socialApp == SocialMediaApp.SYSTEM
        val isKeyboardPkg = socialApp == SocialMediaApp.KEYBOARD
        if (!isOwnAppPkg && !isSystemPkg && !isKeyboardPkg) {
            updateInstagramActive(isInstagramPkg)
        }
        if (!isInstagramPkg) {
            return ScreenData(
                isInstagram = isInstagramActive,
                screen = if (isSystemPkg || isKeyboardPkg) lastScreen else InstagramScreen.NONE,
                isPassive = if (isSystemPkg || isKeyboardPkg) isPassive(lastScreen) else false
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
    fun getScreenInformation(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult? {
        val data: ScreenData = getScreen(pkg, root)
        if (!data.isInstagram) {
            currentScreenSinceMs = nowMs
            passiveSinceMs = nowMs
            return null
        }

        val currentApp = SocialMediaApp.find(pkg)
        val screen = data.screen
        val isP = data.isPassive

        when (currentApp) {
            SocialMediaApp.INSTAGRAM -> {
                lastKnownApp = SocialMediaApp.INSTAGRAM
                if (screen == InstagramScreen.NONE) noneScreenStreak++ else noneScreenStreak = 0
                val canUpdate = screen != InstagramScreen.NONE || noneScreenStreak >= NONE_SCREEN_CONFIRM_COUNT
                if (canUpdate && screen != currentScreen) {
                    currentScreen = screen
                    currentScreenSinceMs = nowMs
                }
                if (canUpdate && isP != isPassive) {
                    isPassive = isP
                    passiveSinceMs = nowMs
                }
            }
            SocialMediaApp.INTERVENTION -> {
                lastKnownApp = SocialMediaApp.INTERVENTION
                if (isPassive) {
                    isPassive = false
                    passiveSinceMs = nowMs
                }
            }
            SocialMediaApp.SYSTEM, SocialMediaApp.KEYBOARD -> {
            }
            else -> { /* ignore */ }
        }

        return PassiveDetectionResult(
            app = lastKnownApp,
            screen = currentScreen,
            screenMs = if(lastKnownApp == SocialMediaApp.INTERVENTION) 0 else nowMs - currentScreenSinceMs,
            passiveMs = if (isPassive) nowMs - passiveSinceMs else 0,
            isPassive = isPassive,
            isInvervention = isPassive ||
                pkg == SocialMediaApp.INTERVENTION.pkg ||
                pkg == SocialMediaApp.SYSTEM.pkg ||
                pkg == SocialMediaApp.KEYBOARD.pkg
        )
    }
}