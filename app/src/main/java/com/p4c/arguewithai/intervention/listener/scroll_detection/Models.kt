package com.p4c.arguewithai.intervention.listener.scroll_detection

enum class ShortFormApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram")
}

interface AppScreen {
}

enum class InstagramScreen : AppScreen {
    FEED,
    FEED_MENU,
    FEED_WEB_VIEW,
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
    NULL
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}