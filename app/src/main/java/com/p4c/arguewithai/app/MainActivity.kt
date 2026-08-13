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
    private val startIVCode: String = "start2026"
    private val stopIVCode: String = "stop"
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
                        val remoteValue = interventionRepo.getEnabledOrNull()
                        val localValue = InterventionPrefs.isEnabled(this@MainActivity)

                        val finalEnabled = remoteValue ?: localValue

                        if (remoteValue == null) {
                            interventionRepo.setEnabled(finalEnabled)
                        }

                        if (finalEnabled) {
                            InterventionPrefs.enable(this@MainActivity)
                        } else {
                            InterventionPrefs.disable(this@MainActivity)
                        }
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
            val nowEnabled = InterventionPrefs.isEnabled(this@MainActivity)

            if (nowEnabled && code == stopIVCode) {
                InterventionPrefs.disable(this@MainActivity)
                updateInterventionState(false)
            } else if (!nowEnabled && code == startIVCode) {
                InterventionPrefs.enable(this@MainActivity)
                updateInterventionState(true)
            } else {
                Toast.makeText(this@MainActivity, "코드가 올바르지 않거나 상태가 맞지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateInterventionState(isEnabled: Boolean) {
        uiScope.launch(Dispatchers.IO) {
            runCatching {
                interventionRepo.setEnabled(isEnabled)
            }.onFailure { e ->
                Logger.e("Failed to save intervention to Firestore", e)
            }
        }
        interventionText.text = getInterventionText()
        val msg = if (isEnabled) "개입이 켜졌습니다." else "개입이 꺼졌습니다."
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
        return if (InterventionPrefs.isEnabled(this)) {
            "✅ 개입 기능이 활성화되어 있습니다."
        } else {
            "❌ 개입 기능이 비활성화되어 있습니다."
        }
    }
}
