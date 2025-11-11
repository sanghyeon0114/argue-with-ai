package com.p4c.arguewithai

import android.content.Context
import androidx.core.content.edit

object InterventionPrefs {
    private const val PREFS = "argue_prefs"
    private const val KEY = "intervention_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY,false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, enabled)
        }
    }

    fun toggle(context: Context): Boolean {
        val now = !isEnabled(context)
        setEnabled(context, now)
        return now
    }
}
