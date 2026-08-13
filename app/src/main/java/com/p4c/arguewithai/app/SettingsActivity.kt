package com.p4c.arguewithai.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.profiles.FirestoreInterventionRepository
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val interventionRepo by lazy { FirestoreInterventionRepository() }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val switchDebugOverlay = findViewById<Switch>(R.id.switchDebugOverlay)
        switchDebugOverlay.isChecked = DebugOverlayPrefs.isEnabled(this)
        switchDebugOverlay.setOnCheckedChangeListener { _, isChecked ->
            DebugOverlayPrefs.setEnabled(this, isChecked)
        }

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

                Toast.makeText(this, "✅ 타입이 ${typeInt}(으)로 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ 0, 1, 2 중 하나만 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnRestorePipYoutube).setOnClickListener {
            OnboardingPrefs.openPipSettingsForApp(this, "com.google.android.youtube")
        }
        findViewById<Button>(R.id.btnRestorePipInstagram).setOnClickListener {
            OnboardingPrefs.openPipSettingsForApp(this, "com.instagram.android")
        }
    }
}
