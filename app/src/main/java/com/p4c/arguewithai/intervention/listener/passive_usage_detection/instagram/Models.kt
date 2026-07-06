package com.p4c.arguewithai.intervention.listener.passive_usage_detection.instagram

import com.p4c.arguewithai.intervention.listener.passive_usage_detection.ShortFormApp

interface AppScreen {
}

enum class InstagramScreen : AppScreen {
    FEED,
    FEED_MENU,
    FEED_WEB_VIEW,
    NOTIFICATION,
    REELS,
    REELS_MENU,
    REELS_AUDIO_MENU,
    DM,
    SEARCH,
    MY_PROFILE,
    MY_SUBSCRIBE_LIST,
    OTHER_PROFILE,
    OTHER_SUBSCRIBE_LIST,
    REPLY,
    STORY,
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}