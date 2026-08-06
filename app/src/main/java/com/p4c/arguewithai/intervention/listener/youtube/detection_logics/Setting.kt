package com.p4c.arguewithai.intervention.listener.youtube.detection_logics

import android.view.accessibility.AccessibilityNodeInfo

object Setting {
    private const val TITLE_ID = "android:id/title"
    private const val GENERAL_TEXT = "일반"
    private const val ACCOUNT_SWITCH_TEXT = "계정 전환 또는 관리"

    fun isSettingScreen(root: AccessibilityNodeInfo): Boolean {
        val titles = root.findAccessibilityNodeInfosByViewId(TITLE_ID)
            ?.filter { it.isVisibleToUser }
            ?.mapNotNull { it.text?.toString() }
            ?: emptyList()

        return titles.contains(GENERAL_TEXT) && titles.contains(ACCOUNT_SWITCH_TEXT)
    }
}
