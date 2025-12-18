package com.p4c.arguewithai.app

import android.content.Context
import androidx.core.content.edit

object InterventionPrefs {
    private const val PREFS = "argue_prefs"
    private const val KEY = "intervention_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, true)
        }
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, false)
        }
    }
}