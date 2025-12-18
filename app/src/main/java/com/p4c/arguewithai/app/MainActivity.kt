package com.p4c.arguewithai.app

import android.R
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.p4c.arguewithai.repository.FirestoreAccessibilityRepository
import com.p4c.arguewithai.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.edit
import androidx.core.net.toUri
import com.p4c.arguewithai.platform.accessibility.MyAccessibilityService
import com.p4c.arguewithai.repository.FirestoreInterventionRepository
import com.p4c.arguewithai.repository.FirestoreUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var accessibilityText: TextView
    private lateinit var nameSection: LinearLayout
    private lateinit var interventionText: TextView
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val accKey = "last_accessibility_enabled"
    private val accessRepo by lazy { FirestoreAccessibilityRepository() }
    private val userRepo by lazy { FirestoreUserRepository() }
    private val interventionRepo by lazy { FirestoreInterventionRepository() }
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

                setView()

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
                setView()
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

    private fun setView() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        fun divider(): View = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 32
                bottomMargin = 32
            }
            setBackgroundColor(getColor(R.color.darker_gray))
        }

        nameSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(nameSection)
        layout.addView(divider())

        userRepo.getUserName { name ->
            runOnUiThread {
                if (!name.isNullOrBlank()) {
                    renderNameDisplay(name)
                } else {
                    renderNameInput()
                }
            }
        }

        val overlayInfoText = TextView(this).apply {
            text = "오버레이 권한을 허용해주세요."
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        val overlayBtn = Button(this).apply {
            text = "오버레이 권한 설정"
            setOnClickListener { requestOverlayPermission() }
        }

        layout.addView(overlayInfoText)
        layout.addView(overlayBtn)
        layout.addView(divider())

        val accessibilityInfoText = TextView(this).apply {
            text = "숏폼 사용 감지를 위해 접근성 서비스 활성화가 필요합니다."
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        accessibilityText = TextView(this).apply {
            text = serviceStatusText()
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        val accessibilityBtn = Button(this).apply {
            text = "접근성 서비스 설정"
            setOnClickListener { openAccessibilitySettingsCompat() }
        }

        layout.addView(accessibilityInfoText)
        layout.addView(accessibilityText)
        layout.addView(accessibilityBtn)
        layout.addView(divider())

        val pipInfoText = TextView(this).apply {
            text = getString(com.p4c.arguewithai.R.string.pip_permission_message)
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        val youtubePIPBtn = Button(this).apply {
            text = getString(com.p4c.arguewithai.R.string.youtube_pip_permission_message)
            setOnClickListener { openPipSettingsForApp(it.context, "com.google.android.youtube") }
        }
        val instagramPIPBtn = Button(this).apply {
            text = getString(com.p4c.arguewithai.R.string.instagram_pip_permission_message)
            setOnClickListener { openPipSettingsForApp(it.context, "com.instagram.android") }
        }

        layout.addView(pipInfoText)
        layout.addView(youtubePIPBtn)
        layout.addView(instagramPIPBtn)
        layout.addView(divider())

        interventionText = TextView(this).apply {
            text = getInterventionText()
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val inputCode = EditText(this).apply {
            hint = "인증 코드"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val interventionBtn = Button(this).apply {
            text = "개입 시작/종료"

            setOnClickListener {
                val code = inputCode.text.toString().trim()
                val nowEnabled = InterventionPrefs.isEnabled(this@MainActivity)

                if (nowEnabled) {
                    if (code == "stop") { // code to off intervention
                        InterventionPrefs.disable(this@MainActivity)
                        uiScope.launch(Dispatchers.IO) {
                            runCatching {
                                interventionRepo.setEnabled(false)
                            }.onFailure { e ->
                                Logger.e("Failed to save intervention to Firestore", e)
                            }
                        }
                        interventionText.text = getInterventionText()
                        Toast.makeText(
                            this@MainActivity,
                            "개입이 꺼졌습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {

                    if (code == "startpain2025") { // code to on intervention
                        InterventionPrefs.enable(this@MainActivity)
                        uiScope.launch(Dispatchers.IO) {
                            runCatching {
                                interventionRepo.setEnabled(true)
                            }.onFailure { e ->
                                Logger.e("Failed to save intervention to Firestore", e)
                            }
                        }
                        interventionText.text = getInterventionText()
                        Toast.makeText(
                            this@MainActivity,
                            "개입이 켜졌습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        layout.addView(interventionText)
        layout.addView(inputCode)
        layout.addView(interventionBtn)

        setContentView(layout)
    }

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

        when {
            tryStart(generalIntent) -> Unit
            else -> {
                Toast.makeText(this, com.p4c.arguewithai.R.string.accessibility_error, Toast.LENGTH_LONG).show()
            }
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
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
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

    private fun renderNameInput() {
        nameSection.removeAllViews()

        val title = TextView(this).apply {
            text = "연구 참여자 이름(닉네임) 입력"
            textSize = 18f
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
        }

        val input = EditText(this).apply {
            hint = "이름 또는 닉네임"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val saveBtn = Button(this).apply {
            text = "이름 저장"
            setOnClickListener {
                val name = input.text.toString().trim()
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
        }

        nameSection.addView(title)
        nameSection.addView(input)
        nameSection.addView(saveBtn)
    }
    private fun renderNameDisplay(name: String) {
        nameSection.removeAllViews()

        val label = TextView(this).apply {
            text = "참여자 이름"
            textSize = 14f
            setPadding(0, 0, 0, 6)
            gravity = Gravity.CENTER
        }
        val nameView = TextView(this).apply {
            text = name
            textSize = 20f
            setPadding(0, 0, 0, 6)
            gravity = Gravity.CENTER
        }

        nameSection.addView(label)
        nameSection.addView(nameView)
    }

    private fun getInterventionText(): String {
        val enabled = InterventionPrefs.isEnabled(this)
        return if (enabled) {
            "✅ 개입 기능이 활성화되어 있습니다."
        } else {
            "❌ 개입 기능이 비활성화되어 있습니다."
        }
    }
}
