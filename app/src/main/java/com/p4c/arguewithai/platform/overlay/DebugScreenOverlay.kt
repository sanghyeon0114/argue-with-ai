package com.p4c.arguewithai.platform.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class DebugScreenOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null

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

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40
        }

        windowManager?.addView(textView, params)
        overlayView = textView
    }

    fun stop() {
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
    }

    fun update(text: String) {
        overlayView?.text = text
    }
}