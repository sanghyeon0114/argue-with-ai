package com.p4c.arguewithai.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.profiles.FirestoreAccessibilityRepository
import com.p4c.arguewithai.repository.profiles.FirestoreInterventionRepository
import com.p4c.arguewithai.repository.profiles.FirestoreUserRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private lateinit var interventionText: TextView
    private val appCheckText: TextView by lazy { findViewById(R.id.tvAppCheckStatus) }

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val accKey = "last_accessibility_enabled"

    private val accessRepo by lazy { FirestoreAccessibilityRepository() }
    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val weekCodes: Map<String, Int> = mapOf(
        "w11011" to InterventionPrefs.WEEK_BASELINE,
        "w20202" to InterventionPrefs.WEEK_INTERVENTION,
        "w30333" to InterventionPrefs.WEEK_WASHOUT,
    )
    private val SETTINGS_PASSWORD: String = "qwe123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        Logger.setLoggerEnabled(true)
        val ctx = applicationContext
        Logger.d("pkg = ${ctx.packageName}")

        try {
            val opts = FirebaseApp.getInstance().options
            Logger.d("projectId = ${opts.projectId}")
            Logger.d("appId     = ${opts.applicationId}")
            Logger.d("apiKey    = ${opts.apiKey}")
        } catch (e: Exception) {
            Logger.e("FirebaseApp.getInstance() FAILED", e)
        }

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onFirebaseReady()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { onFirebaseReady() }
                .addOnFailureListener {
                    Logger.e("❌[Firebase] Login failed", it)
                    setupViews()
                    if (::interventionText.isInitialized) {
                        interventionText.text = getInterventionText()
                    }
                }
        }
    }

    private fun onFirebaseReady() {
        Logger.d("✅[Firebase] Logged in: ${FirebaseAuth.getInstance().currentUser?.uid}")

        setupViews()

        uiScope.launch(Dispatchers.IO) {
            runCatching {
                interventionRepo.syncLocalFromRemoteIfExists(this@MainActivity)
                val remoteWeek = interventionRepo.getWeekOrNull()
                val localWeek = InterventionPrefs.getWeek(this@MainActivity)

                val finalWeek = remoteWeek ?: localWeek

                if (remoteWeek == null) {
                    interventionRepo.setWeek(finalWeek)
                }

                InterventionPrefs.setWeek(this@MainActivity, finalWeek)
            }.onSuccess {
                launch(Dispatchers.Main) {
                    if (::interventionText.isInitialized) {
                        interventionText.text = getInterventionText()
                    }
                }
            }.onFailure { e ->
                Logger.e("Intervention remote sync failed", e)
                launch(Dispatchers.Main) {
                    if (::interventionText.isInitialized) {
                        interventionText.text = getInterventionText()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!OnboardingPrefs.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val enabled = OnboardingPrefs.isAccessibilityGranted(this)
        val last = prefs.getBoolean(accKey, false)

        if (last != enabled && FirebaseAuth.getInstance().currentUser != null) {
            accessRepo.setAccessibilityEnabled(enabled) { ok ->
                if (ok) prefs.edit {
                    putBoolean(accKey, enabled)
                }
            }
        }
    }

    private fun setupViews() {
        // [0. 세팅 버튼 설정]
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettingsMenu() }

        // [App Check 토큰 확인 버튼]
        findViewById<Button>(R.id.btnCheckAppCheck).setOnClickListener { button ->
            checkAppCheckToken(button as Button)
        }

        // [1. 참여자 이름 표시]
        val nameText = findViewById<TextView>(R.id.tvNameDisplay)
        OnboardingPrefs.getCachedName(this)?.let { nameText.text = it }
        if (FirebaseAuth.getInstance().currentUser != null) {
            userRepo.getUserName { name ->
                runOnUiThread {
                    if (!name.isNullOrBlank()) {
                        OnboardingPrefs.setCachedName(this@MainActivity, name)
                        nameText.text = name
                    }
                }
            }
        }

        // [2. 개입 상태 변경]
        interventionText = findViewById(R.id.tvInterventionStatus)
        interventionText.text = getInterventionText()

        val etInterventionCode = findViewById<EditText>(R.id.etInterventionCode)
        findViewById<Button>(R.id.btnToggleIntervention).setOnClickListener {
            val code = etInterventionCode.text.toString().trim()
            val targetWeek = weekCodes[code]
            val currentWeek = InterventionPrefs.getWeek(this@MainActivity)

            if (targetWeek == null) {
                Toast.makeText(this@MainActivity, "코드가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            } else if (targetWeek == currentWeek) {
                Toast.makeText(this@MainActivity, "이미 ${currentWeek}주차입니다.", Toast.LENGTH_SHORT).show()
            } else {
                InterventionPrefs.setWeek(this@MainActivity, targetWeek)
                updateInterventionState(targetWeek)
                etInterventionCode.text.clear()
            }
        }
    }

    private fun updateInterventionState(week: Int) {
        uiScope.launch(Dispatchers.IO) {
            runCatching {
                interventionRepo.setWeek(week)
            }.onFailure { e ->
                Logger.e("Failed to save intervention week to Firestore", e)
            }
        }
        interventionText.text = getInterventionText()
        val msg = if (InterventionPrefs.isEnabledForWeek(week)) {
            "${week}주차로 설정되었습니다. 개입이 켜졌습니다."
        } else {
            "${week}주차로 설정되었습니다. 개입이 꺼졌습니다."
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsMenu() {
        val input = EditText(this).apply {
            hint = "비밀번호 입력"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = FrameLayout(this).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("관리자 페이지")
            .setView(container)
            .setPositiveButton("확인") { _, _ ->
                val entered = input.text.toString().trim()
                if (entered == SETTINGS_PASSWORD) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    Toast.makeText(this, "인증되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun checkAppCheckToken(button: Button) {
        button.isEnabled = false
        appCheckText.text = "확인 중..."
        uiScope.launch {
            val start = SystemClock.elapsedRealtime()
            val result = runCatching {
                withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) {
                    FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start

            result.onSuccess { token ->
                if (token == null) {
                    Logger.e("⏱️[AppCheck] No response in ${APP_CHECK_TIMEOUT_MS}ms")
                    appCheckText.text = "⏱️ 응답 없음 (${APP_CHECK_TIMEOUT_MS / 1000}초 초과)"
                } else {
                    val expiresInSec = (token.expireTimeMillis - System.currentTimeMillis()) / 1000
                    Logger.d("✅[AppCheck] Token OK (${elapsed}ms, expires in ${expiresInSec}s)")
                    appCheckText.text = "✅ 받음 (${elapsed}ms, 만료까지 ${expiresInSec / 60}분)"
                }
            }.onFailure { e ->
                Logger.e("❌[AppCheck] Token failed (${elapsed}ms)", e)
                appCheckText.text = "❌ 발급 실패\n${e.javaClass.simpleName}: ${e.message}"
            }
            button.isEnabled = true
        }
    }

    private fun getInterventionText(): String {
        val week = InterventionPrefs.getWeek(this)
        return if (InterventionPrefs.isEnabledForWeek(week)) {
            "✅ ${week}주차 - 개입 기능이 활성화되어 있습니다."
        } else {
            "❌ ${week}주차 - 개입 기능이 비활성화되어 있습니다."
        }
    }

    companion object {
        private const val APP_CHECK_TIMEOUT_MS = 8_000L
    }
}
