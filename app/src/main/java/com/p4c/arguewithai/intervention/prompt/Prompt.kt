package com.p4c.arguewithai.intervention.prompt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.chat.activity.BlockingActivity
import com.p4c.arguewithai.chat.activity.LlmChatbotActivity
import com.p4c.arguewithai.chat.activity.RuleBasedChatbotActivity
import com.p4c.arguewithai.repository.SessionId
import com.p4c.arguewithai.utils.Logger

class Prompt(
    private val context: Context,
    private val onClosed: (reason: String) -> Unit = {}
) {
    var isVisible: Boolean = false
        private set

    private val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            val reason = resultData?.getString("reason") ?: "unknown"
            Logger.d("ChatActivity closed. reason=$reason")
            isVisible = false
            onClosed(reason)
        }
    }

    fun show(sessionId: SessionId?) {
        if (isVisible) {
            Logger.d("❌ showPrompt: already visible, skip")
            return
        }
        if (!InterventionPrefs.isEnabled(context)) {
            Logger.d("❌ showPrompt: intervention disabled, skip")
            return
        }
        isVisible = true

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val interventionType = prefs.getInt("intervention_type", 0)

        val targetActivityClass = when (interventionType) {
            0 -> BlockingActivity::class.java
            1 -> RuleBasedChatbotActivity::class.java
            2 -> LlmChatbotActivity::class.java
            else -> BlockingActivity::class.java
        }

        val i = Intent(context, targetActivityClass)

        if (interventionType == 1 || interventionType == 2) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            i.putExtra("receiver", resultReceiver)
            i.putExtra("session_id", sessionId?.value ?: "")
        } else {
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        context.startActivity(i)
    }

    fun reset() {
        isVisible = false
    }
}
