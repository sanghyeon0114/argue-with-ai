package com.p4c.arguewithai.intervention.listener.session_time

enum class SessionApp(val pkg: String?, val label: String) {
    YOUTUBE("com.google.android.youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    TIKTOK("com.ss.android.ugc.trill", "TikTok"),
    MYAPP("com.p4c.arguewithai", "ArgueWithAi"),
    SYSTEM("com.android.systemui", "SYSTEM"),
    KEYBOARD("com.samsung.android.honeyboard", "KEYBOARD"),
    NULL(null, "NULL")
}

interface SessionViewCallback {
    fun onEnter(app: SessionApp, sinceMs: Long) {}
    fun onExit(app: SessionApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: SessionApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}