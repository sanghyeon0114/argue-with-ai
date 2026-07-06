package com.p4c.arguewithai.intervention.listener.session

enum class SessionApp(val pkg: String?, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    MYAPP("com.p4c.arguewithai", "ArgueWithAi"),
//    YOUTUBE("com.google.android.youtube", "YouTube"),
//    SYSTEM("com.android.systemui", "SYSTEM"),
//    KEYBOARD("com.samsung.android.honeyboard", "KEYBOARD"),
//    NULL(null, "NULL")
}

interface SessionViewCallback {
    fun onEnter(app: SessionApp, sinceMs: Long) {}
    fun onExit(app: SessionApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: SessionApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}