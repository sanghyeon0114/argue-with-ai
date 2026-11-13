package com.p4c.arguewithai.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        setupPilotMode()
        setupTlx()

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

    override fun onResume() {
        super.onResume()
        // 개입을 마치고 돌아왔을 때 기록 목록 갱신
        refreshPilotRecords()
        refreshTlxRecords()
    }

    // ---------------------------
    // 파일럿 테스트 모드
    // ---------------------------
    private val listTimeFmt = SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA)
    /** 목록에서 체크한 기록 id */
    private val selectedRecordIds = mutableSetOf<String>()

    private fun setupPilotMode() {
        val switchPilot = findViewById<Switch>(R.id.switchPilotMode)
        switchPilot.isChecked = PilotPrefs.isEnabled(this)
        switchPilot.setOnCheckedChangeListener { _, isChecked ->
            PilotPrefs.setEnabled(this, isChecked)
            val msg = if (isChecked) "파일럿 테스트 모드가 켜졌습니다." else "파일럿 테스트 모드가 꺼졌습니다."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        val etParticipant = findViewById<EditText>(R.id.etPilotParticipant)
        etParticipant.setText(PilotPrefs.getCurrentParticipant(this))
        findViewById<Button>(R.id.btnSavePilotParticipant).setOnClickListener {
            val memo = etParticipant.text.toString().trim()
            PilotPrefs.setCurrentParticipant(this, memo)
            val msg = if (memo.isEmpty()) "현재 대상자 메모를 비웠습니다." else "이후 기록은 '$memo'(으)로 저장됩니다."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCopyPilotRecords).setOnClickListener {
            val records = PilotPrefs.getAll(this)
            if (records.isEmpty()) {
                Toast.makeText(this, "복사할 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("pilot_records", pilotRecordsTsv(records)))
            Toast.makeText(this, "${records.size}건을 복사했습니다. (TSV, 스프레드시트에 붙여넣기)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDeleteSelectedPilotRecords).setOnClickListener { deleteSelectedRecords() }
    }

    private fun refreshPilotRecords() {
        val tvSummary = findViewById<TextView>(R.id.tvPilotSummary)
        val container = findViewById<LinearLayout>(R.id.llPilotRecords)
        container.removeAllViews()

        val records = PilotPrefs.getAll(this)
        selectedRecordIds.retainAll(records.map { it.id }.toSet())
        updateDeleteSelectedButton()
        if (records.isEmpty()) {
            tvSummary.text = "기록 없음"
            return
        }

        // 대상자 × 조건별 요약
        tvSummary.text = records
            .groupBy { it.memo.ifEmpty { "(대상자 없음)" } to it.condition }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .joinToString("\n") { (key, list) ->
                String.format(
                    Locale.KOREA, "%s / %s: %d회 · 평균 %.1f초 · 평균 %.1f타",
                    key.first, key.second, list.size,
                    list.map { it.durationMs / 1000.0 }.average(), list.map { it.keystrokes }.average()
                )
            }

        // 기록 목록: 체크박스로 골라 삭제, 글자를 누르면 대상자 메모 수정
        val pad = dp(10)
        records.forEachIndexed { i, r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, pad / 2, 0, pad / 2)
            }
            val check = CheckBox(this).apply {
                isChecked = r.id in selectedRecordIds
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedRecordIds.add(r.id) else selectedRecordIds.remove(r.id)
                    updateDeleteSelectedButton()
                }
            }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = String.format(
                    Locale.KOREA, "%d. %s  %s · %.1f초 · %d타 · 메시지 %d개 · %s\n대상자: %s",
                    i + 1, listTimeFmt.format(Date(r.startedAt)), r.condition,
                    r.durationMs / 1000.0, r.keystrokes, r.messages, r.reason,
                    r.memo.ifEmpty { "(없음)" }
                )
                textSize = 12f
                setTextColor(0xFF0A192F.toInt())
                setPadding(pad / 2, pad / 2, pad, pad / 2)
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { showEditRecordDialog(r) }
            }
            row.addView(check)
            row.addView(label)
            container.addView(row)
            if (i < records.lastIndex) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0xFFDDDDDD.toInt())
                })
            }
        }
    }

    private fun updateDeleteSelectedButton() {
        val btn = findViewById<Button>(R.id.btnDeleteSelectedPilotRecords)
        btn.text = if (selectedRecordIds.isEmpty()) "선택한 기록 삭제" else "선택한 기록 삭제 (${selectedRecordIds.size}건)"
        btn.isEnabled = selectedRecordIds.isNotEmpty()
        btn.alpha = if (btn.isEnabled) 1f else 0.4f
    }

    private fun deleteSelectedRecords() {
        val ids = selectedRecordIds.toSet()
        if (ids.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("선택한 기록 삭제")
            .setMessage("선택한 ${ids.size}건의 개입 기록을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                val n = PilotPrefs.deleteAll(this, ids)
                selectedRecordIds.clear()
                Toast.makeText(this, "${n}건을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                refreshPilotRecords()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditRecordDialog(record: PilotPrefs.Record) {
        val input = EditText(this).apply {
            hint = "대상자 메모 (예: P01)"
            setText(record.memo)
            setSelection(text.length)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(String.format(Locale.KOREA, "%s %s", listTimeFmt.format(Date(record.startedAt)), record.condition))
            .setView(box)
            .setPositiveButton("메모 저장") { _, _ ->
                PilotPrefs.updateMemo(this, record.id, input.text.toString())
                refreshPilotRecords()
            }
            .setNeutralButton("이 기록 삭제") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("이 기록을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        PilotPrefs.delete(this, record.id)
                        selectedRecordIds.remove(record.id)
                        refreshPilotRecords()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pilotRecordsTsv(records: List<PilotPrefs.Record>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val header = "no\tparticipant\tcondition\tstarted_at\tduration_sec\tkeystrokes\tmessages\treason"
        val rows = records.mapIndexed { i, r ->
            listOf(
                i + 1, r.memo, r.condition, fmt.format(Date(r.startedAt)),
                String.format(Locale.US, "%.3f", r.durationMs / 1000.0), r.keystrokes, r.messages, r.reason
            ).joinToString("\t")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    // ---------------------------
    // NASA-TLX 설문
    // ---------------------------
    private val tlxItemShort = listOf("정신", "신체", "시간", "수행", "노력", "좌절")
    /** 목록에서 체크한 TLX 응답 id */
    private val selectedTlxIds = mutableSetOf<String>()

    private fun setupTlx() {
        findViewById<Button>(R.id.btnStartTlx).setOnClickListener {
            startActivity(Intent(this, NasaTlxActivity::class.java))
        }

        findViewById<Button>(R.id.btnCopyTlxRecords).setOnClickListener {
            val records = TlxPrefs.getAll(this)
            if (records.isEmpty()) {
                Toast.makeText(this, "복사할 응답이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("tlx_records", tlxRecordsTsv(records)))
            Toast.makeText(this, "${records.size}건을 복사했습니다. (TSV, 스프레드시트에 붙여넣기)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDeleteSelectedTlxRecords).setOnClickListener {
            val ids = selectedTlxIds.toSet()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("선택한 응답 삭제")
                .setMessage("선택한 ${ids.size}건의 NASA-TLX 응답을 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    val n = TlxPrefs.deleteAll(this, ids)
                    selectedTlxIds.clear()
                    Toast.makeText(this, "${n}건을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    refreshTlxRecords()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun refreshTlxRecords() {
        val tvSummary = findViewById<TextView>(R.id.tvTlxSummary)
        val container = findViewById<LinearLayout>(R.id.llTlxRecords)
        container.removeAllViews()

        val records = TlxPrefs.getAll(this)
        selectedTlxIds.retainAll(records.map { it.id }.toSet())
        updateDeleteSelectedTlxButton()
        if (records.isEmpty()) {
            tvSummary.text = "응답 없음"
            return
        }

        // 대상자 × 조건별 평균 (Raw TLX = 6개 항목 평균)
        tvSummary.text = records
            .groupBy { it.participant to it.condition }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .joinToString("\n") { (key, list) ->
                val itemMeans = tlxItemShort.indices.joinToString(", ") { i ->
                    String.format(Locale.KOREA, "%s %.1f", tlxItemShort[i], list.map { it.scores[i] }.average())
                }
                String.format(
                    Locale.KOREA, "%s / %s: %d회 · Raw TLX %.2f\n  (%s)",
                    key.first, key.second, list.size, list.map { it.rawTlx }.average(), itemMeans
                )
            }

        val pad = dp(10)
        records.forEachIndexed { i, r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, pad / 2, 0, pad / 2)
            }
            val check = CheckBox(this).apply {
                isChecked = r.id in selectedTlxIds
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedTlxIds.add(r.id) else selectedTlxIds.remove(r.id)
                    updateDeleteSelectedTlxButton()
                }
            }
            val scores = tlxItemShort.indices.joinToString(" ") { idx -> "${tlxItemShort[idx]}${r.scores[idx]}" }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = String.format(
                    Locale.KOREA, "%d. %s  %s / %s · Raw TLX %.2f\n%s",
                    i + 1, listTimeFmt.format(Date(r.submittedAt)), r.participant, r.condition, r.rawTlx, scores
                )
                textSize = 12f
                setTextColor(0xFF0A192F.toInt())
                setPadding(pad / 2, pad / 2, pad, pad / 2)
            }
            row.addView(check)
            row.addView(label)
            container.addView(row)
            if (i < records.lastIndex) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0xFFDDDDDD.toInt())
                })
            }
        }
    }

    private fun updateDeleteSelectedTlxButton() {
        val btn = findViewById<Button>(R.id.btnDeleteSelectedTlxRecords)
        btn.text = if (selectedTlxIds.isEmpty()) "선택한 응답 삭제" else "선택한 응답 삭제 (${selectedTlxIds.size}건)"
        btn.isEnabled = selectedTlxIds.isNotEmpty()
        btn.alpha = if (btn.isEnabled) 1f else 0.4f
    }

    private fun tlxRecordsTsv(records: List<TlxPrefs.Record>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val header = (listOf("no", "participant", "condition", "submitted_at") + TlxPrefs.ITEM_KEYS + "raw_tlx")
            .joinToString("\t")
        val rows = records.mapIndexed { i, r ->
            (listOf(i + 1, r.participant, r.condition, fmt.format(Date(r.submittedAt))) +
                r.scores + String.format(Locale.US, "%.3f", r.rawTlx)).joinToString("\t")
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
