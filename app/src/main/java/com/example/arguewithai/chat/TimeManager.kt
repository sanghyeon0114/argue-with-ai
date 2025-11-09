package com.example.arguewithai.chat

import android.content.Context
import android.os.SystemClock

object TimeManager {
    private const val PREFS_NAME = "chatbot_cooldown_prefs"
    private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
    private const val DEFAULT_COOLDOWN_MS = 5 * 1000L   // 10분: 10*60*1000L

    var cooldownMillis: Long = DEFAULT_COOLDOWN_MS

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEligible(context: Context): Boolean {
        val lastAt = prefs(context).getLong(KEY_LAST_PROMPT_AT, 0L)
        val now = SystemClock.elapsedRealtime()
        return (now - lastAt) >= cooldownMillis
    }

    fun markShown(context: Context) {
        val now = SystemClock.elapsedRealtime()
        prefs(context).edit().putLong(KEY_LAST_PROMPT_AT, now).apply()
    }

    fun remainingMillis(context: Context): Long {
        val lastAt = prefs(context).getLong(KEY_LAST_PROMPT_AT, 0L)
        val now = SystemClock.elapsedRealtime()
        val remaining = cooldownMillis - (now - lastAt)
        return if (remaining > 0) remaining else 0L
    }

    fun resetCooldown(context: Context) {
        prefs(context).edit().remove(KEY_LAST_PROMPT_AT).apply()
    }
}