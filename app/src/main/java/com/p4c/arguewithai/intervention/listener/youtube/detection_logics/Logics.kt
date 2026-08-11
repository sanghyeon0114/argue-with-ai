package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.intervention.listener.SocialMediaApp
import com.p4c.arguewithai.intervention.listener.youtube.YoutubeScreen

object YoutubeLogics {
    val YOUTUBE_PKG: String = SocialMediaApp.YOUTUBE.pkg

    fun getScreenName(root: AccessibilityNodeInfo): YoutubeScreen {
        return when {
            Search.isSearchScreen(root) -> YoutubeScreen.SEARCH
            Main.isMainScreen(root) && !Search.isSearchScreen(root) -> YoutubeScreen.MAIN
            Setting.isSettingScreen(root) -> YoutubeScreen.SETTING
            Creation.isCreationScreen(root) -> YoutubeScreen.CREATION
            SideBar.isSideBar(root) -> YoutubeScreen.SIDE_BAR
            else -> YoutubeScreen.NONE
        }
    }
    fun isCurrentScreen(screen: YoutubeScreen, root: AccessibilityNodeInfo): Boolean {
        return when (screen) {
            YoutubeScreen.SEARCH -> Search.isSearchScreen(root)
            YoutubeScreen.MAIN -> Main.isMainScreen(root) && !Search.isSearchScreen(root)
            YoutubeScreen.SETTING -> Setting.isSettingScreen(root)
            YoutubeScreen.CREATION -> Creation.isCreationScreen(root)
            YoutubeScreen.SIDE_BAR -> SideBar.isSideBar(root)
            YoutubeScreen.NONE -> false
        }
    }
}