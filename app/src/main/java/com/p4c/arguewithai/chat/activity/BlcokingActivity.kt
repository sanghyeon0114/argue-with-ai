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
import com.p4c.arguewithai.repository.intervention.ExitMethod
import com.p4c.arguewithai.repository.intervention.FirestoreBlockingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BlockingActivityStatus {
    @Volatile var isOpen: Boolean = false
}

class BlockingActivity : ComponentActivity() {
    private val countdownSeconds = 15

    private val repo = FirestoreBlockingRepository()
    private val sessionId: String by lazy {
        intent.getStringExtra("session_id") ?: System.currentTimeMillis().toString()
    }

    private var finished = false

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        BlockingActivityStatus.isOpen = true
        setContentView(R.layout.activity_blocking)
        hideSystemUI()

        lifecycleScope.launch {
            runCatching { repo.logStart(sessionId) }.onFailure { it.printStackTrace() }
        }

        findViewById<TextView>(R.id.tvCenterMessage).text = "잠깐 숨 한번 크게 쉬어볼까요?"
        val tvMessageTime = findViewById<TextView>(R.id.tvMessageTime)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveExit(finished = false, method = ExitMethod.BACK)
            }
        })

        lifecycleScope.launch {
            for (i in countdownSeconds downTo 1) {
                tvMessageTime.text = "${i}s"
                delay(1000L)
            }
            finished = true
            saveExit(finished = true, method = ExitMethod.COMPLETE)
            finish()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStop() {
        super.onStop()
        BlockingActivityStatus.isOpen = false
        if (!finished) saveExit(finished = false, method = ExitMethod.BACKGROUND)
    }

    override fun onDestroy() {
        super.onDestroy()
        BlockingActivityStatus.isOpen = false
    }

    private fun saveExit(finished: Boolean, method: ExitMethod) {
        lifecycleScope.launch {
            runCatching { repo.logExit(sessionId, finished, method, null) }
                .onFailure { it.printStackTrace() }
        }
    }
}