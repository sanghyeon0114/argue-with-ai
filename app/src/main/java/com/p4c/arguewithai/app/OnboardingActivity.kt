package com.p4c.arguewithai.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.profiles.FirestoreInterventionRepository
import com.p4c.arguewithai.repository.profiles.FirestoreUserRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class OnboardingActivity : ComponentActivity() {

    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }

    private lateinit var stepName: LinearLayout
    private lateinit var stepOverlay: LinearLayout
    private lateinit var stepAccessibility: LinearLayout
    private lateinit var stepPip: LinearLayout
    private lateinit var stepType: LinearLayout
    private lateinit var tvStepProgress: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var etNameInput: EditText
    private lateinit var etTypeInput: EditText
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnSaveName: Button
    private lateinit var btnSaveType: Button
    private lateinit var tvNameServerStatus: TextView
    private lateinit var tvTypeServerStatus: TextView

    private var verifiedName: String? = null
    private var verifiedType: Int? = null
    private var saving = false
    private var saveStage = ""
    private var pipYoutubeOpened = false
    private var pipInstagramOpened = false

    private val steps by lazy { listOf(stepName, stepOverlay, stepAccessibility, stepPip, stepType) }

    private var currentStep = 0

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
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        etNameInput = findViewById(R.id.etNameInput)
        etTypeInput = findViewById(R.id.etTypeInput)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSaveName = findViewById(R.id.btnSaveName)
        btnSaveType = findViewById(R.id.btnSaveType)
        tvNameServerStatus = findViewById(R.id.tvNameServerStatus)
        tvTypeServerStatus = findViewById(R.id.tvTypeServerStatus)

        currentStep = savedInstanceState?.getInt(KEY_STEP, 0) ?: 0
        pipYoutubeOpened = savedInstanceState?.getBoolean(KEY_PIP_YT, false) ?: false
        pipInstagramOpened = savedInstanceState?.getBoolean(KEY_PIP_IG, false) ?: false

        setupNameStep()
        setupOverlayStep()
        setupAccessibilityStep()
        setupPipStep()
        setupTypeStep()
        setupNavigation()

        ensureSignedInThenResolveName()
        showStep(currentStep)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        updateNextButton()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, currentStep)
        outState.putBoolean(KEY_PIP_YT, pipYoutubeOpened)
        outState.putBoolean(KEY_PIP_IG, pipInstagramOpened)
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
        if (OnboardingPrefs.getCachedName(this) != null) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        userRepo.getUserName { name ->
            if (!name.isNullOrBlank()) {
                OnboardingPrefs.setCachedName(this, name)
                runOnUiThread {
                    verifiedName = name
                    if (!isFinishing && etNameInput.text.isNullOrBlank()) {
                        etNameInput.setText(name)
                    }
                    updateNextButton()
                }
            }
        }
    }

    private fun setupNameStep() {
        OnboardingPrefs.getCachedName(this)?.let { etNameInput.setText(it) }
        etNameInput.addTextChangedListener(nextButtonWatcher)

        btnSaveName.setOnClickListener {
            val name = etNameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch { saveName(name) }
        }
    }

    private suspend fun saveName(name: String): Boolean {
        OnboardingPrefs.setCachedName(this, name)
        tvNameServerStatus.text = "서버 저장 중..."
        val (ok, err) = runServerSave(btnSaveName) {
            saveStage = "문서 확인(서버 읽기)"
            userRepo.ensureUserDoc()
            saveStage = "이름 쓰기·검증"
            userRepo.setUserNameAndVerify(name)
        }
        if (ok) verifiedName = name
        updateNextButton()
        tvNameServerStatus.text =
            if (ok) "서버 등록 확인됨 ✅ ($name)" else "서버 등록 실패 ❌ ($err)"
        toast(if (ok) "✅ 이름이 서버에 등록되었습니다." else "❌ 이름 서버 등록 실패. 다시 저장해주세요.")
        return ok
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
            pipYoutubeOpened = true
            markPipIfDone()
            OnboardingPrefs.openPipSettingsForApp(this, "com.google.android.youtube")
        }
        findViewById<Button>(R.id.btnPipInstagram).setOnClickListener {
            pipInstagramOpened = true
            markPipIfDone()
            OnboardingPrefs.openPipSettingsForApp(this, "com.instagram.android")
        }
    }

    private fun markPipIfDone() {
        if (pipYoutubeOpened && pipInstagramOpened) {
            OnboardingPrefs.setPipConfirmed(this, true)
        }
    }

    private fun setupTypeStep() {
        OnboardingPrefs.getInterventionType(this)?.let { etTypeInput.setText(it.toString()) }
        etTypeInput.addTextChangedListener(nextButtonWatcher)

        btnSaveType.setOnClickListener {
            val type = parseType()
            if (type == null) {
                Toast.makeText(this, "❌ 0, 1, 2 중 하나만 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch { saveType(type) }
        }
    }

    private fun parseType(): Int? =
        etTypeInput.text.toString().trim().toIntOrNull()?.takeIf { it in 0..2 }

    private suspend fun saveType(type: Int): Boolean {
        OnboardingPrefs.setInterventionType(this, type)
        tvTypeServerStatus.text = "서버 저장 중..."
        val (ok, err) = runServerSave(btnSaveType) {
            saveStage = "문서 확인(서버 읽기)"
            userRepo.ensureUserDoc()
            saveStage = "개입 타입 쓰기·검증"
            interventionRepo.setInterventionTypeAndVerify(type)
        }
        if (ok) verifiedType = type
        updateNextButton()
        tvTypeServerStatus.text =
            if (ok) "서버 등록 확인됨 ✅ (타입 $type)" else "서버 등록 실패 ❌ ($err)"
        toast(if (ok) "✅ 개입 타입이 서버에 등록되었습니다." else "❌ 개입 타입 서버 등록 실패. 다시 저장해주세요.")
        return ok
    }

    private suspend fun runServerSave(
        button: Button,
        block: suspend () -> Boolean
    ): Pair<Boolean, String> {
        setSaving(true, button)
        try {
            saveStage = "로그인 확인"
            return withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                if (!ensureSignedIn()) return@withTimeoutOrNull false to "로그인 실패"
                val first = runCatching { block() }
                if (first.getOrNull() == true) return@withTimeoutOrNull true to ""

                val e = first.exceptionOrNull()
                if (e != null) Logger.e("Onboarding server save failed", e)
                if (!isAuthProblem(e)) {
                    return@withTimeoutOrNull false to (e?.let { describe(it) } ?: "저장 값 불일치")
                }

                Logger.d("Auth problem on save; re-signing in anonymously and retrying")
                FirebaseAuth.getInstance().signOut()
                saveStage = "재로그인"
                if (!ensureSignedIn()) return@withTimeoutOrNull false to "재로그인 실패"
                val second = runCatching { block() }
                second.exceptionOrNull()?.let { Logger.e("Onboarding server save retry failed", it) }
                if (second.getOrNull() == true) true to ""
                else false to (second.exceptionOrNull()?.let { describe(it) } ?: "저장 값 불일치")
            } ?: run {
                Logger.e("Onboarding server save timed out at stage: $saveStage")
                false to "시간 초과 – 단계: $saveStage"
            }
        } finally {
            setSaving(false, button)
        }
    }

    private fun isAuthProblem(e: Throwable?): Boolean {
        val code = (e as? FirebaseFirestoreException)?.code ?: return false
        return code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.UNAUTHENTICATED
    }

    private fun describe(e: Throwable): String {
        val code = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "로그인 안 됨"
        return "$code: ${e.message}\nuid=$uid"
    }

    private suspend fun ensureSignedIn(): Boolean {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            saveStage = "로그인 토큰 갱신"
            val valid = runCatching { user.getIdToken(true).await() }
                .onFailure { e -> Logger.e("Token refresh failed", e) }
                .exceptionOrNull()
                .let { it !is FirebaseAuthInvalidUserException }
            if (valid) return true
            auth.signOut()
        }
        saveStage = "익명 로그인"
        return runCatching { auth.signInAnonymously().await() }
            .onFailure { e -> Logger.e("Anonymous sign-in failed", e) }
            .isSuccess && auth.currentUser != null
    }

    private fun setSaving(on: Boolean, button: Button) {
        saving = on
        button.isEnabled = !on
        btnPrev.isEnabled = !on
        btnNext.isEnabled = !on
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setupNavigation() {
        btnPrev.setOnClickListener {
            if (!saving && currentStep > 0) showStep(currentStep - 1)
        }
        btnNext.setOnClickListener {
            if (saving || !isStepDone(currentStep)) return@setOnClickListener
            if (currentStep < steps.lastIndex) {
                showStep(currentStep + 1)
            } else {
                OnboardingPrefs.setOnboardingCompleted(this, true)
                goToMain()
            }
        }
    }

    private fun isStepDone(step: Int): Boolean = when (step) {
        0 -> verifiedName != null && etNameInput.text.toString().trim() == verifiedName
        1 -> OnboardingPrefs.isOverlayGranted(this)
        2 -> OnboardingPrefs.isAccessibilityGranted(this)
        3 -> OnboardingPrefs.isPipConfirmed(this)
        4 -> verifiedType != null && parseType() == verifiedType
        else -> false
    }

    private fun updateNextButton() {
        btnNext.visibility = if (isStepDone(currentStep)) View.VISIBLE else View.INVISIBLE
    }

    private val nextButtonWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (::btnNext.isInitialized) updateNextButton()
        }
    }

    private fun showStep(index: Int) {
        currentStep = index.coerceIn(0, steps.lastIndex)
        steps.forEachIndexed { i, view ->
            view.visibility = if (i == currentStep) View.VISIBLE else View.GONE
        }
        tvStepProgress.text = "${currentStep + 1} / ${steps.size}"
        btnPrev.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        btnNext.text = if (currentStep == steps.lastIndex) "시작하기" else "다음"
        refreshStatus()
        updateNextButton()
    }

    private fun refreshStatus() {
        tvAccessibilityStatus.text =
            if (OnboardingPrefs.isAccessibilityGranted(this)) "상태: 활성화됨 ✅" else "상태: 비활성화됨 ❌"
        tvOverlayStatus.text =
            if (OnboardingPrefs.isOverlayGranted(this)) "상태: 허용됨 ✅" else "상태: 허용 안 됨 ❌"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val KEY_STEP = "onboarding_step"
        private const val SAVE_TIMEOUT_MS = 15_000L
        private const val KEY_PIP_YT = "pip_youtube_opened"
        private const val KEY_PIP_IG = "pip_instagram_opened"
    }
}
