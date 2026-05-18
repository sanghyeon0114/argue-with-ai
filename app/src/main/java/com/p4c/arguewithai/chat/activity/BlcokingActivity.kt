package com.p4c.arguewithai.chat.activity

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.p4c.arguewithai.R
import com.p4c.arguewithai.chat.prompts.AffirmationPrompts
import com.p4c.arguewithai.repository.intervention.BlockingMessage
import com.p4c.arguewithai.repository.intervention.ExitMethod
import com.p4c.arguewithai.repository.intervention.FirestoreBlockingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BlockingActivityStatus {
    @Volatile var isOpen: Boolean = false
}

class BlockingActivity : ComponentActivity() {
    val countPage: Int = 3
    val secondPerPage: Int = 10

    private val repo = FirestoreBlockingRepository()
    private val sessionId: String by lazy {
        intent.getStringExtra("session_id") ?: System.currentTimeMillis().toString()
    }

    private var finished: Boolean = false

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching { repo.logStart(sessionId) }.onFailure { it.printStackTrace() }
        }
        BlockingActivityStatus.isOpen = true
        setContentView(R.layout.activity_blocking)
        hideSystemUI()

        val tvCenterMessage = findViewById<TextView>(R.id.tvCenterMessage)
        val tvMessagePage   = findViewById<TextView>(R.id.tvMessagePage)
        val tvMessageTime   = findViewById<TextView>(R.id.tvMessageTime)

        tvCenterMessage.text = "지금 이 영상을 보게 된 이유가 무엇인지 생각해보세요."

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveExit(finished = false, method = ExitMethod.BACK)
            }
        })

        lifecycleScope.launch {
            for (page in 1..countPage) {
                val currentMessage = AffirmationPrompts.getPrompt(page - 1)
                tvCenterMessage.text = currentMessage
                saveBlockingStep(currentMessage, page)
                for (i in secondPerPage downTo 1) {
                    tvMessageTime.text = "${i}s"
                    tvMessagePage.text = "${page}/3"
                    delay(1000L)
                }
            }
            finished = true
            saveExit(finished = true, method = ExitMethod.COMPLETE)
            finish()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BlockingActivityStatus.isOpen = false
    }

    override fun onStop() {
        super.onStop()
        BlockingActivityStatus.isOpen = false
        if (!finished) {
            saveExit(finished = false, method = ExitMethod.BACKGROUND)
        }
    }

    private fun saveBlockingStep(prompt: String, step: Int) {
        lifecycleScope.launch {
            runCatching {
                val message = BlockingMessage(sessionId, prompt)
                repo.updateMessage(message, step)
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun saveExit(finished: Boolean, method: ExitMethod, note: String? = null) {
        lifecycleScope.launch {
            runCatching {
                repo.logExit(sessionId, finished, method, note)
            }.onFailure { it.printStackTrace() }
        }
    }
}