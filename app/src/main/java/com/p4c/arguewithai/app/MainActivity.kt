package com.p4c.arguewithai.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.R
import com.p4c.arguewithai.platform.accessibility.MyAccessibilityService
import com.p4c.arguewithai.repository.FirestoreAccessibilityRepository
import com.p4c.arguewithai.repository.FirestoreInterventionRepository
import com.p4c.arguewithai.repository.FirestoreUserRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var accessibilityText: TextView
    private lateinit var interventionText: TextView

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val accKey = "last_accessibility_enabled"

    private val accessRepo by lazy { FirestoreAccessibilityRepository() }
    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val startIVCode: String = "start2026"
    private val stopIVCode: String = "stop"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. 여기서 우리가 만든 XML 화면을 연결합니다!
        setContentView(R.layout.activity_settings)

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

                // 2. XML 뷰들을 찾아 클릭 이벤트 등을 연결하는 함수 호출
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

        if (::accessibilityText.isInitialized) {
            accessibilityText.text = serviceStatusText()
        }

        val enabled = isMyAccessibilityServiceEnabled()
        val last = prefs.getBoolean(accKey, false)

        if (last != enabled) {
            accessRepo.setAccessibilityEnabled(enabled) { ok ->
                if (ok) prefs.edit {
                    putBoolean(accKey, enabled)
                }
            }
        }
    }

    // --- 기존의 복잡했던 setView()를 대체하는 새 함수 ---
    private fun setupViews() {
        // [1. 이름 설정]
        val etNameInput = findViewById<EditText>(R.id.etNameInput)
        val btnSaveName = findViewById<Button>(R.id.btnSaveName)

        userRepo.getUserName { name ->
            runOnUiThread {
                if (!name.isNullOrBlank()) {
                    renderNameDisplay(name)
                } else {
                    renderNameInput()
                }
            }
        }

        btnSaveName.setOnClickListener {
            val name = etNameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this@MainActivity, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            userRepo.setUserName(name) { ok ->
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(this@MainActivity, "✅ 이름이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        renderNameDisplay(name)
                    } else {
                        Toast.makeText(this@MainActivity, "❌ 저장 실패 (네트워크 확인)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // [2. 오버레이 권한]
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlayPermission() }

        // [3. 접근성 서비스]
        accessibilityText = findViewById(R.id.tvAccessibilityStatus)
        accessibilityText.text = serviceStatusText()
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener { openAccessibilitySettingsCompat() }

        // [4. PIP 권한]
        findViewById<Button>(R.id.btnPipYoutube).setOnClickListener { openPipSettingsForApp(this, "com.google.android.youtube") }
        findViewById<Button>(R.id.btnPipInstagram).setOnClickListener { openPipSettingsForApp(this, "com.instagram.android") }

        // [5. 개입 액티비티 타입 설정]
        val etTypeInput = findViewById<EditText>(R.id.etTypeInput)
        val currentType = prefs.getInt("intervention_type", 0)
        etTypeInput.setText(currentType.toString())

        findViewById<Button>(R.id.btnSaveType).setOnClickListener {
            val typeInt = etTypeInput.text.toString().trim().toIntOrNull()
            if (typeInt != null && typeInt in 0..2) {
                prefs.edit { putInt("intervention_type", typeInt) }
                Toast.makeText(this@MainActivity, "✅ 타입이 $typeInt(으)로 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "❌ 0, 1, 2 중 하나만 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // [6. 개입 상태 변경]
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

    // 이름 입력란과 표시란의 Visibility(숨김/보임)를 교체하는 로직
    private fun renderNameDisplay(name: String) {
        findViewById<LinearLayout>(R.id.layoutNameInput).visibility = View.GONE
        findViewById<LinearLayout>(R.id.layoutNameDisplay).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvNameDisplay).text = name
    }

    private fun renderNameInput() {
        findViewById<LinearLayout>(R.id.layoutNameInput).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.layoutNameDisplay).visibility = View.GONE
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

    // --- 아래는 기존 유틸리티 함수들 (변경 없음) ---
    private fun serviceStatusText(): String {
        return if (isMyAccessibilityServiceEnabled()) {
            "상태: 활성화됨 ✅"
        } else {
            "상태: 비활성화됨 ❌"
        }
    }

    private fun isMyAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ComponentName(this, MyAccessibilityService::class.java)
        return enabled.any {
            val s = it.resolveInfo.serviceInfo
            s.packageName == target.packageName && s.name == target.className
        }
    }

    private fun openAccessibilitySettingsCompat() {
        val generalIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (!tryStart(generalIntent)) {
            Toast.makeText(this, R.string.accessibility_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryStart(intent: Intent): Boolean {
        return try {
            val canHandle = intent.resolveActivity(packageManager) != null
            if (!canHandle) return false
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "이미 오버레이 권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }

    fun openPipSettingsForApp(context: Context, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").apply {
                    data = "package:$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                Toast.makeText(context, "PIP 설정 화면을 열 수 없어 앱 설정으로 이동합니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "PIP는 Android 8.0 이상에서만 지원됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInterventionText(): String {
        return if (InterventionPrefs.isEnabled(this)) {
            "✅ 개입 기능이 활성화되어 있습니다."
        } else {
            "❌ 개입 기능이 비활성화되어 있습니다."
        }
    }
}