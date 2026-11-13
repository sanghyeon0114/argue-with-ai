package com.p4c.arguewithai.app

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.p4c.arguewithai.utils.KeystrokeCounter
import java.util.Locale

/**
 * 개입 화면 1회분의 파일럿 측정. 개입 Activity 의 onCreate 에서 만들고,
 * 사용자 메시지가 전송될 때마다 [onUserMessage], 화면이 닫힐 때 [finish] 를 부른다.
 * 파일럿 테스트 모드가 꺼져 있으면 아무것도 기록하지 않는다.
 */
class PilotSession(private val condition: String) {
    private val startedAt = System.currentTimeMillis()
    private val startElapsed = SystemClock.elapsedRealtime()
    private var keystrokes = 0
    private var messages = 0
    private var finished = false

    fun onUserMessage(text: String) {
        keystrokes += KeystrokeCounter.count(text)
        messages++
    }

    fun finish(context: Context, reason: String) {
        if (finished) return
        finished = true
        val app = context.applicationContext
        if (!PilotPrefs.isEnabled(app)) return

        val durationMs = SystemClock.elapsedRealtime() - startElapsed
        val memo = PilotPrefs.getCurrentParticipant(app)
        PilotPrefs.add(
            app,
            PilotPrefs.Record(PilotPrefs.newId(), condition, startedAt, durationMs, keystrokes, messages, reason, memo)
        )

        val who = if (memo.isNotEmpty()) "$memo · " else ""
        val msg = String.format(Locale.KOREA, "[파일럿] %s%s · %.1f초 · %d타", who, condition, durationMs / 1000.0, keystrokes)
        Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
    }
}
