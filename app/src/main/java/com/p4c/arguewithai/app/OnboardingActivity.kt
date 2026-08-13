package com.p4c.arguewithai.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.profiles.FirestoreInterventionRepository
import com.p4c.arguewithai.repository.profiles.FirestoreUserRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var stepName: LinearLayout
    private lateinit var stepOverlay: LinearLayout
    private lateinit var stepAccessibility: LinearLayout
    private lateinit var stepPip: LinearLayout
    private lateinit var stepType: LinearLayout
    private lateinit var tvStepProgress: TextView
    private lateinit var tvAccessibilityStatus: TextView

    private val steps by lazy { listOf(stepName, stepOverlay, stepAccessibility, stepPip, stepType) }

    private var nameCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        stepName = findViewById(R.id.stepName)
        stepOverlay = findViewById(R.id.stepOverlay)
        stepAccessibility = findViewById(R.id.stepAccessibility)
        stepPip = findViewById(R.id.stepPip)
        stepType = findViewById(R.id.stepType)
        tvStepProgress = findViewById(R.id.tvStepProgress)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)

        setupNameStep()
        setupOverlayStep()
        setupAccessibilityStep()
        setupPipStep()
        setupTypeStep()

        ensureSignedInThenResolveName()
    }

    override fun onResume() {
        super.onResume()
        if (nameCheckDone) {
            showCurrentStep()
        }
    }

    private fun ensureSignedInThenResolveName() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            resolveCachedOrRemoteName()
            return
        }
        auth.signInAnonymously()
            .addOnCompleteListener {
                resolveCachedOrRemoteName()
            }
    }

    private fun resolveCachedOrRemoteName() {
        if (OnboardingPrefs.getCachedName(this) != null) {
            nameCheckDone = true
            return
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            nameCheckDone = true
            runOnUiThread { if (!isFinishing) showCurrentStep() }
            return
        }
        userRepo.getUserName { name ->
            if (!name.isNullOrBlank()) {
                OnboardingPrefs.setCachedName(this, name)
            }
            nameCheckDone = true
            runOnUiThread { if (!isFinishing) showCurrentStep() }
        }
    }

    private fun setupNameStep() {
        val etNameInput = findViewById<EditText>(R.id.etNameInput)
        OnboardingPrefs.getCachedName(this)?.let { etNameInput.setText(it) }

        findViewById<Button>(R.id.btnSaveName).setOnClickListener {
            val name = etNameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            OnboardingPrefs.setCachedName(this, name)

            if (FirebaseAuth.getInstance().currentUser != null) {
                userRepo.setUserName(name) { ok ->
                    if (!ok) {
                        runOnUiThread {
                            Toast.makeText(this, "❌ 서버 저장 실패 (네트워크 확인). 이름은 우선 저장하고 계속 진행합니다.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            showCurrentStep()
        }
    }

    private fun setupOverlayStep() {
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            OnboardingPrefs.requestOverlayPermission(this)
        }
    }

    private fun setupAccessibilityStep() {
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            OnboardingPrefs.openAccessibilitySettings(this)
        }
    }

    private fun setupPipStep() {
        findViewById<Button>(R.id.btnPipYoutube).setOnClickListener {
            OnboardingPrefs.openPipSettingsForApp(this, "com.google.android.youtube")
        }
        findViewById<Button>(R.id.btnPipInstagram).setOnClickListener {
            OnboardingPrefs.openPipSettingsForApp(this, "com.instagram.android")
        }
        findViewById<Button>(R.id.btnPipDone).setOnClickListener {
            OnboardingPrefs.setPipConfirmed(this, true)
            showCurrentStep()
        }
    }

    private fun setupTypeStep() {
        val etTypeInput = findViewById<EditText>(R.id.etTypeInput)
        OnboardingPrefs.getInterventionType(this)?.let { etTypeInput.setText(it.toString()) }

        findViewById<Button>(R.id.btnSaveType).setOnClickListener {
            val typeInt = etTypeInput.text.toString().trim().toIntOrNull()
            if (typeInt != null && typeInt in 0..2) {
                OnboardingPrefs.setInterventionType(this, typeInt)

                if (FirebaseAuth.getInstance().currentUser != null) {
                    ioScope.launch {
                        runCatching { interventionRepo.setInterventionType(typeInt) }
                            .onFailure { e -> Logger.e("Firestore setInterventionType failed", e) }
                    }
                }

                showCurrentStep()
            } else {
                Toast.makeText(this, "❌ 0, 1, 2 중 하나만 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCurrentStep() {
        tvAccessibilityStatus.text =
            if (OnboardingPrefs.isAccessibilityGranted(this)) "상태: 활성화됨 ✅" else "상태: 비활성화됨 ❌"

        val stepIndex = when {
            OnboardingPrefs.getCachedName(this) == null -> 0
            !OnboardingPrefs.isOverlayGranted(this) -> 1
            !OnboardingPrefs.isAccessibilityGranted(this) -> 2
            !OnboardingPrefs.isPipConfirmed(this) -> 3
            OnboardingPrefs.getInterventionType(this) == null -> 4
            else -> -1
        }

        if (stepIndex == -1) {
            goToMain()
            return
        }

        steps.forEachIndexed { index, view ->
            view.visibility = if (index == stepIndex) View.VISIBLE else View.GONE
        }
        tvStepProgress.text = "${stepIndex + 1} / ${steps.size}"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
