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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BlockingActivityStatus {
    @Volatile var isOpen: Boolean = false
}

class BlockingActivity : ComponentActivity() {

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        BlockingActivityStatus.isOpen = true
        setContentView(R.layout.activity_blocking)
        hideSystemUI()

        val tvCenterMessage = findViewById<TextView>(R.id.tvCenterMessage)
        val tvMessageTime = findViewById<TextView>(R.id.tvMessageTime)

        tvCenterMessage.text = "지금 이 영상을 보게 된 이유가 무엇인지 생각해보세요."

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })

        lifecycleScope.launch {
            for (i in 10 downTo 1) {
                tvMessageTime.text = "${i}s"
                delay(1000L)
            }
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
    }
}