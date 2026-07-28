package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.p4c.arguewithai.intervention.listener.PassiveDetectionResult
import com.p4c.arguewithai.utils.Logger
import java.util.concurrent.TimeUnit

class ResultDebugOverlay(private val service: AccessibilityService) {

    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

    private var overlayView: TextView? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 16
        y = 120
    }

    // 반드시 메인 스레드에서 호출 (onAccessibilityEvent는 메인 루퍼에서 실행됨)
    fun show(result: PassiveDetectionResult?, hasIntervened: Boolean) {
        val text = formatResult(result, hasIntervened)
        val view = overlayView
        if (view == null) {
            val textView = TextView(service).apply {
                setBackgroundColor(Color.parseColor("#55000000"))
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(16, 8, 16, 8)
                this.text = text
            }
            overlayView = textView
            runCatching { windowManager.addView(textView, layoutParams) }
                .onFailure { Logger.e("debug overlay addView 실패", it) }
        } else {
            view.text = text
        }
    }

    fun hide() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
    }

    private fun formatResult(result: PassiveDetectionResult?, hasIntervened: Boolean): String {
        if (result == null) return "result: null\n개입됨: $hasIntervened"
        return buildString {
            appendLine("app: ${result.app}")
            appendLine("screen: ${result.screen}")
            appendLine("screenMs: ${formatDuration(result.screenMs)}")
            appendLine("isPassive: ${result.isPassive}")
            appendLine("passiveMs: ${formatDuration(result.passiveMs)}")
            appendLine("개입 조건 충족: ${result.isInvervention}")
            append("개입됨: $hasIntervened")
        }
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val ms = millis % 1000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, ms)
    }
}
