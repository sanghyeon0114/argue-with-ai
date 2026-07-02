package com.p4c.arguewithai.intervention.listener.scroll

enum class ShortFormApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram")
}

interface AppScreen {
}

enum class InstagramScreen : AppScreen {
    HOME,
    REELS,
    DM,
    SEARCH,
    PROFILE,
    FEED_MENU,
    NULL
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}