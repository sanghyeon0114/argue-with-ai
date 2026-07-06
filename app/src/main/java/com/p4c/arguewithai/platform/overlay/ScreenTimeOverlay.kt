package com.p4c.arguewithai.platform.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class ScreenTimeOverlay(
    private val context: Context,
    private val tickIntervalMs: Long = 200L
) {
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    private var currentLabel: String = "NONE"
    private var enteredAt: Long = 0L

    fun start() {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val textView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#88000000"))
            gravity = Gravity.CENTER
            text = "NONE"
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 40
        }

        windowManager?.addView(textView, params)
        overlayView = textView

        enteredAt = System.currentTimeMillis()
        startTicking()
    }

    fun stop() {
        stopTicking()
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
    }

    fun update(label: String) {
        if (label != currentLabel) {
            currentLabel = label
            enteredAt = System.currentTimeMillis()
        }
        render(System.currentTimeMillis())
    }

    private fun startTicking() {
        stopTicking()
        val runnable = object : Runnable {
            override fun run() {
                render(System.currentTimeMillis())
                handler.postDelayed(this, tickIntervalMs)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun render(nowMs: Long) {
        overlayView?.text = if (currentLabel == "NONE") {
            currentLabel
        } else {
            val elapsedMs = nowMs - enteredAt
            "$currentLabel\n${formatElapsed(elapsedMs)}"
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }
}