package com.p4c.arguewithai.utils

object DurationFormatter {
    /** ms -> "mm:ss" */
    fun format(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}