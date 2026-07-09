package com.p4c.arguewithai.intervention.listener

interface AppScreen

interface SMCallback {
    fun onEnter(app: SocialMediaApp, sinceMs: Long) {}
    fun onExit(app: SocialMediaApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: SocialMediaApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}