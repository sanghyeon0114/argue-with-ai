package com.p4c.arguewithai.intervention.listener.youtube

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.intervention.listener.youtube.detection_logics.YoutubeLogics
import com.p4c.arguewithai.utils.Logger

data class ScreenData(
    var isYoutbe: Boolean,
    val screen: YoutubeScreen,
    val isPassive: Boolean
)

class YoutubeTracker {

    private var isYoutubeActive: Boolean = false
    private var youtubeHitStreak: Int = 0
    private var notYoutubeHitStreak: Int = 0
    companion object {
        private const val YOUTUBE_ENTER_CONFIRM_COUNT = 5
        private const val YOUTUBE_EXIT_CONFIRM_COUNT = 5
    }
    private fun updateInstagramActive(isYoutubePkg: Boolean) {
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
        val screen = YoutubeLogics.getScreenName(root)
        Logger.d("$screen")
        return ScreenData(
            false,
            YoutubeScreen.NONE,
            false
        )
    }

    fun getScreenInformation(pkg: String, root: AccessibilityNodeInfo, nowMs: Long): PassiveDetectionResult? {
        val data: ScreenData = getScreen(pkg, root)
        //Logger.d("$data")
        return null
    }
}