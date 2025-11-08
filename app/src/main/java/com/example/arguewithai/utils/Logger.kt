package com.example.arguewithai.utils

import android.util.Log

object Logger {
    var enabled = true
    private const val TAG = "MyService"

    fun d(msg: String) { if (enabled) Log.d(TAG, msg) }
    fun e(msg: String, t: Throwable? = null) { if (enabled) Log.e(TAG, msg, t) }
    fun w(msg: String) { if (enabled) Log.w(TAG, msg) }
}