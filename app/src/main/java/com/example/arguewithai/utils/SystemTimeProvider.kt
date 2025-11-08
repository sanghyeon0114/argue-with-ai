package com.example.arguewithai.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface TimeProvider {
    fun nowMs(): Long
    fun dayUTC(ms: Long = nowMs()): String
}

class SystemTimeProvider : TimeProvider {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun dayUTC(ms: Long): String = sdf.format(Date(ms))
}