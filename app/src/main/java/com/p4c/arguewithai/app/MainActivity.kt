package com.p4c.arguewithai.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    private lateinit var interventionText: TextView

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val accKey = "last_accessibility_enabled"

    private val accessRepo by lazy { FirestoreAccessibilityRepository() }
    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // 주차 설정 코드: 1주차(baseline, 개입 X) / 2주차(개입 O) / 3주차(개입 제거 후 관찰, 개입 X)
    private val weekCodes: Map<String, Int> = mapOf(
        "w11011" to InterventionPrefs.WEEK_BASELINE,
        "w20202" to InterventionPrefs.WEEK_INTERVENTION,
        "w30333" to InterventionPrefs.WEEK_WASHOUT,
    )
    private val SETTINGS_PASSWORD: String = "qwe123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!OnboardingPrefs.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

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

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                Logger.d("✅[Firebase] Logged in: ${it.user?.uid}")

                setupViews()

                uiScope.launch(Dispatchers.IO) {
                    runCatching {
                        interventionRepo.syncLocalFromRemoteIfExists(this@MainActivity)
                        val remoteWeek = interventionRepo.getWeekOrNull()
                        val localWeek = InterventionPrefs.getWeek(this@MainActivity)

                        val finalWeek = remoteWeek ?: localWeek

                        // Firestore 에 기록이 없을 때만 현재 주차를 채워 넣는다 (updatedAt 이 매 실행마다 바뀌지 않도록)
                        if (remoteWeek == null) {
                            interventionRepo.setWeek(finalWeek)
                        }

                        // 개입 여부는 주차에서 파생 (2주차일 때만 ON)
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
            .addOnFailureListener {
                Logger.e("❌[Firebase] Login failed", it)
                setupViews()
                if (::interventionText.isInitialized) {
                    interventionText.text = getInterventionText()
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

        if (last != enabled) {
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

        // [1. 참여자 이름 표시]
        val nameText = findViewById<TextView>(R.id.tvNameDisplay)
        OnboardingPrefs.getCachedName(this)?.let { nameText.text = it }
        userRepo.getUserName { name ->
            runOnUiThread {
                if (!name.isNullOrBlank()) {
                    OnboardingPrefs.setCachedName(this@MainActivity, name)
                    nameText.text = name
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

    private fun getInterventionText(): String {
        val week = InterventionPrefs.getWeek(this)
        return if (InterventionPrefs.isEnabledForWeek(week)) {
            "✅ ${week}주차 - 개입 기능이 활성화되어 있습니다."
        } else {
            "❌ ${week}주차 - 개입 기능이 비활성화되어 있습니다."
        }
    }
}
