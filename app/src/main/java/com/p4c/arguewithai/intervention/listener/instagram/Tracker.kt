package com.p4c.arguewithai.intervention.listener.instagram

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.instagram.detection_logics.InstagramLogics
import com.p4c.arguewithai.utils.Logger

class Tracker {
    private var lastScreen: InstagramScreen? = null

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
        return if (screen == InstagramScreen.FEED || screen == InstagramScreen.REELS || screen == InstagramScreen.SEARCH) {
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