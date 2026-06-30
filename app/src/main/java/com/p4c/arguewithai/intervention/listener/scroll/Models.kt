package com.p4c.arguewithai.intervention.listener.scroll

enum class ShortFormApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram")
}

interface AppScreen {
    val label: String
}

enum class InstagramScreen(override val label: String) : AppScreen {
    HOME("home"),
    REELS("reels"),
    DM("dm"),
    SEARCH("search"),
    PROFILE("profile"),
    WATCH_REELS("watch_reels"),
    NULL("null")
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}