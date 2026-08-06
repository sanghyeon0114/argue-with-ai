package com.p4c.arguewithai.intervention.listener.youtube

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.youtube.detection_logics.YoutubeLogics
import com.p4c.arguewithai.utils.Logger

data class ScreenData(
    var isYoutube: Boolean,
    val screen: YoutubeScreen,
    val isPassive: Boolean
)

class YoutubeTracker {
    private var lastScreen: YoutubeScreen = YoutubeScreen.NONE
    private var currentScreen: YoutubeScreen = YoutubeScreen.NONE
    private var currentScreenSinceMs: Long = 0L
    private var isPassive: Boolean = false

    private var isYoutubeActive: Boolean = false
    private var youtubeHitStreak: Int = 0
    private var notYoutubeHitStreak: Int = 0
    private var passiveSinceMs: Long = 0L
    private var lastKnownApp: SocialMediaApp = SocialMediaApp.YOUTUBE


    companion object {
        private const val YOUTUBE_ENTER_CONFIRM_COUNT = 10
        private const val YOUTUBE_EXIT_CONFIRM_COUNT = 10
        private val PASSIVE_SCREEN = setOf(
            YoutubeScreen.MAIN,
            YoutubeScreen.SIDE_BAR,
        )
        private fun isPassive(screen: YoutubeScreen): Boolean {
            return screen in PASSIVE_SCREEN
        }
    }

    private fun updateYoutubeActive(isYoutubePkg: Boolean) {
        if (isYoutubePkg) {
            youtubeHitStreak++
            notYoutubeHitStreak = 0
        } else {
            notYoutubeHitStreak++
            youtubeHitStreak = 0
        }
        if (!isYoutubeActive && youtubeHitStreak >= YOUTUBE_ENTER_CONFIRM_COUNT) {
            isYoutubeActive = true
        } else if (isYoutubeActive && notYoutubeHitStreak >= YOUTUBE_EXIT_CONFIRM_COUNT) {
            isYoutubeActive = false
        }
    }

    fun getScreen(pkg: String, root: AccessibilityNodeInfo): ScreenData {
        val isYoutubePkg = pkg == YoutubeLogics.YOUTUBE_PKG
        val socialApp = SocialMediaApp.find(pkg)
        val isOwnAppPkg = socialApp == SocialMediaApp.INTERVENTION
        val isSystemPkg = socialApp == SocialMediaApp.SYSTEM
        val isKeyboardPkg = socialApp == SocialMediaApp.KEYBOARD
        if (!isOwnAppPkg && !isSystemPkg && !isKeyboardPkg) {
            updateYoutubeActive(isYoutubePkg)
        }
        if (!isYoutubePkg) {
            return ScreenData(
                isYoutube = isYoutubeActive,
                screen = if (isSystemPkg || isKeyboardPkg) lastScreen else YoutubeScreen.NONE,
                isPassive = if (isSystemPkg || isKeyboardPkg) isPassive(lastScreen) else false
            )
        }
        if (YoutubeLogics.isCurrentScreen(lastScreen, root)) {
            return ScreenData(
                isYoutube = isYoutubeActive,
                screen = lastScreen,
                isPassive = isPassive(lastScreen)
            )
        }
        val screen = YoutubeLogics.getScreenName(root)
        if (screen == YoutubeScreen.NONE) {
            return ScreenData(
                isYoutube = isYoutubeActive,
                screen = YoutubeScreen.NONE,
                isPassive = isPassive(YoutubeScreen.NONE)
            )
        }
        if (screen != lastScreen) {
            lastScreen = screen
        }
        return ScreenData(
            isYoutube = isYoutubeActive,
            screen = lastScreen,
            isPassive = isPassive(lastScreen)
        )
    }

    fun getScreenInformation(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult? {
        val data: ScreenData = getScreen(pkg, root)
        Logger.d("$data")
        if (!data.isYoutube) {
            currentScreenSinceMs = nowMs
            passiveSinceMs = nowMs
            return null
        }

        val currentApp = SocialMediaApp.find(pkg)
        val screen = data.screen
        val isP = data.isPassive

        when (currentApp) {
            SocialMediaApp.YOUTUBE -> {
                lastKnownApp = SocialMediaApp.YOUTUBE
                if (screen != YoutubeScreen.NONE) {
                    if (screen != currentScreen) {
                        currentScreen = screen
                        currentScreenSinceMs = nowMs
                    }
                    if (isP != isPassive) {
                        isPassive = isP
                        passiveSinceMs = nowMs
                    }
                }
            }
            SocialMediaApp.INTERVENTION -> {
                if (lastKnownApp != SocialMediaApp.INTERVENTION) {
                    currentScreenSinceMs = nowMs
                }
                lastKnownApp = SocialMediaApp.INTERVENTION
                if (isPassive) {
                    isPassive = false
                    passiveSinceMs = nowMs
                }
            }
            SocialMediaApp.KEYBOARD -> {
                if (lastKnownApp != SocialMediaApp.INTERVENTION) {
                    currentScreenSinceMs = nowMs
                    passiveSinceMs = nowMs
                }
            }
            SocialMediaApp.SYSTEM -> {
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