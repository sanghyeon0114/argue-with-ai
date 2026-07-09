package com.p4c.arguewithai.intervention.listener

class ScreenDurationTracker<T>(
    private val nullResetThresholdMs: Long = 500L
) {
    private var lastDetected: T? = null
    private var lastDetectedSinceMs: Long = 0L
    private var nullSinceMs: Long? = null

    fun forceReset(nowMs: Long) {
        lastDetected = null
        lastDetectedSinceMs = nowMs
        nullSinceMs = null
    }

    fun update(detected: T?, nowMs: Long): Long {
        if (detected != null) {
            nullSinceMs = null
            if (detected != lastDetected) {
                lastDetected = detected
                lastDetectedSinceMs = nowMs
            }
        } else if (lastDetected != null) {
            if (nullSinceMs == null) {
                nullSinceMs = nowMs
            }
            val nullElapsed = nowMs - nullSinceMs!!
            if (nullElapsed >= nullResetThresholdMs) {
                lastDetected = null
                lastDetectedSinceMs = nowMs
                nullSinceMs = null
            }
        }
        return nowMs - lastDetectedSinceMs
    }

    fun current(): T? = lastDetected
}