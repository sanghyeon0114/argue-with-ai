package com.p4c.arguewithai.app

import android.content.Intent
import android.os.Build
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.ParticipantId
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.ACCESSIBILITY
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.COMPLETED
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.NAME
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.OVERLAY
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.PIP
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.Companion.TYPE
import com.p4c.arguewithai.repository.profiles.FirestoreOnboardingRepository.WriteMode
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class OnboardingActivity : ComponentActivity() {

    private val repo by lazy { FirestoreOnboardingRepository() }

    private lateinit var stepName: LinearLayout
    private lateinit var stepOverlay: LinearLayout
    private lateinit var stepAccessibility: LinearLayout
    private lateinit var stepPip: LinearLayout
    private lateinit var stepType: LinearLayout
    private lateinit var tvStepProgress: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvServerStatus: TextView
    private lateinit var etNameInput: EditText
    private lateinit var etTypeInput: EditText
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnSaveName: Button
    private lateinit var btnSaveType: Button

    private val steps by lazy { listOf(stepName, stepOverlay, stepAccessibility, stepPip, stepType) }

    private var currentStep = 0

    private var verifiedName: String? = null
    private var verifiedType: Int? = null

    private var saving = false
    private var saveStage = ""
    private var pipYoutubeOpened = false
    private var pipInstagramOpened = false
    private var loadJob: Job? = null
    private var lastWriteMode: WriteMode? = null

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
        tvServerStatus = findViewById(R.id.tvServerStatus)
        etNameInput = findViewById(R.id.etNameInput)
        etTypeInput = findViewById(R.id.etTypeInput)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSaveName = findViewById(R.id.btnSaveName)
        btnSaveType = findViewById(R.id.btnSaveType)

        currentStep = savedInstanceState?.getInt(KEY_STEP, 0) ?: 0
        pipYoutubeOpened = savedInstanceState?.getBoolean(KEY_PIP_YT, false) ?: false
        pipInstagramOpened = savedInstanceState?.getBoolean(KEY_PIP_IG, false) ?: false

        setupNameStep()
        setupOverlayStep()
        setupAccessibilityStep()
        setupPipStep()
        setupTypeStep()
        setupNavigation()

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

    // ───────────────────────── 단계별 UI ─────────────────────────

    private fun setupNameStep() {
        ParticipantId.toNumber(OnboardingPrefs.getCachedName(this))?.let { etNameInput.setText(it.toString()) }
        etNameInput.addTextChangedListener(nextButtonWatcher)

        btnSaveName.setOnClickListener {
            val pid = inputParticipantId()
            if (pid == null) {
                toast("1 이상의 자연수를 입력해주세요.")
                return@setOnClickListener
            }
            OnboardingPrefs.setCachedName(this, pid)
            lifecycleScope.launch {
                if (saveStep(btnSaveName, "참여자 $pid", "userInfo", mapOf(NAME to pid))) {
                    verifiedName = pid
                    if (lastWriteMode == WriteMode.UPDATED) {
                        tvServerStatus.text = "⚠️ $pid 문서가 이미 있어 기존 문서에 이어서 저장했습니다.\n번호가 맞는지 확인해주세요."
                    }
                }
                updateNextButton()
            }
        }
    }

    private fun inputParticipantId(): String? =
        etNameInput.text.toString().trim().toIntOrNull()?.let { ParticipantId.fromNumber(it) }

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
                toast("❌ 0, 1, 2 중 하나만 입력해주세요.")
                return@setOnClickListener
            }
            OnboardingPrefs.setInterventionType(this, type)
            lifecycleScope.launch {
                if (saveStep(btnSaveType, "개입 타입", "intervention", mapOf(TYPE to type))) {
                    verifiedType = type
                }
                updateNextButton()
            }
        }
    }

    private fun parseType(): Int? =
        etTypeInput.text.toString().trim().toIntOrNull()?.takeIf { it in 0..2 }

    // ───────────────────────── 이동 ─────────────────────────

    private fun setupNavigation() {
        btnPrev.setOnClickListener {
            if (!saving && currentStep > 0) showStep(currentStep - 1)
        }
        btnNext.setOnClickListener {
            if (saving || !isStepDone(currentStep)) return@setOnClickListener
            lifecycleScope.launch {
                val ok = when (currentStep) {
                    1 -> saveStep(btnNext, "오버레이 권한", "onboarding", mapOf(OVERLAY to true))
                    2 -> saveStep(
                        btnNext, "접근성 서비스", "accessibility", mapOf(ACCESSIBILITY to true),
                        extra = mapOf(
                            "accessibility.device" to mapOf(
                                "manufacturer" to Build.MANUFACTURER,
                                "model" to Build.MODEL,
                                "sdkInt" to Build.VERSION.SDK_INT
                            )
                        )
                    )
                    3 -> saveStep(btnNext, "PIP 설정", "onboarding", mapOf(PIP to true))
                    4 -> saveStep(btnNext, "온보딩 완료", "onboarding", mapOf(COMPLETED to true))
                    else -> true
                }
                if (!ok) return@launch

                if (currentStep < steps.lastIndex) {
                    showStep(currentStep + 1)
                } else {
                    OnboardingPrefs.setOnboardingCompleted(this@OnboardingActivity, true)
                    goToMain()
                }
            }
        }
    }

    private fun isStepDone(step: Int): Boolean = when (step) {
        0 -> verifiedName != null && inputParticipantId() == verifiedName
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
        loadServerState(currentStep)
    }

    private fun refreshStatus() {
        tvAccessibilityStatus.text =
            if (OnboardingPrefs.isAccessibilityGranted(this)) "상태: 활성화됨 ✅" else "상태: 비활성화됨 ❌"
        tvOverlayStatus.text =
            if (OnboardingPrefs.isOverlayGranted(this)) "상태: 허용됨 ✅" else "상태: 허용 안 됨 ❌"
    }

    // ───────────────────────── 서버 확인 ─────────────────────────

    private fun loadServerState(step: Int) {
        loadJob?.cancel()
        if (saving) return
        val pid = if (step == 0) inputParticipantId() else ParticipantId.currentOrNull()
        if (pid == null) {
            tvServerStatus.text = "참여자 번호를 입력하고 저장해주세요. (예: 1 → P1)"
            return
        }
        tvServerStatus.text = "서버 기록 확인 중... ($pid)"
        loadJob = lifecycleScope.launch {
            val snap: DocumentSnapshot? = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                if (!ensureSignedIn()) return@withTimeoutOrNull null
                catchNonCancel { repo.fetchFromServer(pid) }
                    .onFailure { e -> Logger.e("Onboarding fetch failed", e) }
                    .getOrNull()
            }
            if (step != currentStep || saving) return@launch

            val path = when (step) {
                0 -> NAME; 1 -> OVERLAY; 2 -> ACCESSIBILITY; 3 -> PIP; else -> TYPE
            }
            val value = snap?.get(path)
            tvServerStatus.text =
                if (value == null) "$pid: 서버 기록 없음 – 저장 시 새로 생성됩니다."
                else "$pid: 서버 기록 있음 ($value) – 저장 시 수정됩니다."

            when (step) {
                0 -> if (value == pid && pid == ParticipantId.currentOrNull()) verifiedName = pid
                4 -> (value as? Number)?.toInt()?.takeIf { it in 0..2 }?.let { type ->
                    if (etTypeInput.text.isNullOrBlank()) {
                        etTypeInput.setText(type.toString())
                        OnboardingPrefs.setInterventionType(this@OnboardingActivity, type)
                    }
                    if (parseType() == type) verifiedType = type
                }
            }
            updateNextButton()
        }
    }

    // ───────────────────────── 서버 저장 ─────────────────────────

    private suspend fun saveStep(
        button: Button,
        label: String,
        section: String,
        fields: Map<String, Any>,
        extra: Map<String, Any> = emptyMap()
    ): Boolean {
        loadJob?.cancel()
        tvServerStatus.text = "$label 서버 저장 중..."
        var mode: WriteMode? = null
        lastWriteMode = null
        val (ok, err) = runServerSave(button) {
            saveStage = "$label 확인·저장·검증"
            val outcome = repo.upsertAndVerify(fields, section, extra)
            mode = outcome.mode
            outcome.verified
        }
        lastWriteMode = mode
        val how = when (mode) {
            WriteMode.CREATED -> "새로 생성"
            WriteMode.UPDATED -> "기존 기록 수정"
            null -> ""
        }
        tvServerStatus.text =
            if (ok) "✅ $label 서버 등록 확인됨 ($how)" else "❌ $label 서버 등록 실패 ($err)"
        toast(if (ok) "✅ $label 저장됨 ($how)" else "❌ $label 저장 실패. 다시 시도해주세요.")
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
                val first = catchNonCancel { block() }
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
                val second = catchNonCancel { block() }
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

    private inline fun <T> catchNonCancel(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun isAuthProblem(e: Throwable?): Boolean =
        (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAUTHENTICATED

    private fun describe(e: Throwable): String {
        val code = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "로그인 안 됨"
        return "$code: ${e.message}\nuid=$uid"
    }

    private suspend fun ensureSignedIn(): Boolean {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return true
        saveStage = "익명 로그인"
        return catchNonCancel { auth.signInAnonymously().await() }
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

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val KEY_STEP = "onboarding_step"
        private const val KEY_PIP_YT = "pip_youtube_opened"
        private const val KEY_PIP_IG = "pip_instagram_opened"
        private const val SAVE_TIMEOUT_MS = 15_000L
    }
}
