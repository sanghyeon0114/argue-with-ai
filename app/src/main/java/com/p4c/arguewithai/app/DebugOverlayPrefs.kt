package com.p4c.arguewithai.app

import android.content.Context
import androidx.core.content.edit

object DebugOverlayPrefs {
    private const val PREFS = "argue_prefs"
    const val KEY = "debug_overlay_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, enabled)
        }
    }
}
