package com.p4c.arguewithai.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import com.p4c.arguewithai.R

/**
 * 파일럿 테스트용 NASA-TLX 설문 화면. 6개 항목을 0~10 중에서 고르고 저장한다.
 * 대상자는 파일럿 "현재 대상자"로, 조건은 가장 최근 파일럿 기록의 조건으로 미리 채운다.
 */
class NasaTlxActivity : ComponentActivity() {

    private data class Item(val title: String, val question: String, val low: String, val high: String)

    private val items = listOf(
        Item("정신적 요구", "과제가 정신적으로 얼마나 힘들었습니까?", "매우 낮음", "매우 높음"),
        Item("신체적 요구", "과제가 신체적으로 얼마나 힘들었습니까?", "매우 낮음", "매우 높음"),
        Item("시간적 요구", "과제를 하는 동안 시간에 얼마나 쫓기거나 서둘렀습니까?", "매우 낮음", "매우 높음"),
        Item("수행도", "요구받은 과제를 얼마나 성공적으로 해냈습니까?", "완벽함", "실패"),
        Item("노력", "그 정도의 수행 수준을 얻기 위해 얼마나 열심히 노력해야 했습니까?", "매우 낮음", "매우 높음"),
        Item("좌절감", "과제를 하는 동안 얼마나 불안하고, 의욕이 꺾이고, 짜증 나고, 스트레스를 받고, 성가셨습니까?", "매우 낮음", "매우 높음"),
    )
    private val conditions = listOf("baseline", "affirmation", "justification")

    private val scores = IntArray(6) { -1 }
    private val scaleCells = mutableListOf<List<TextView>>()
    private lateinit var etParticipant: EditText
    private lateinit var rgCondition: RadioGroup

    private val navy = 0xFF0A192F.toInt()
    private val gray = 0x800A192F.toInt()
    private val blue = 0xFF2F6FED.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        content.addView(text("NASA-TLX", 22f, navy, bold = true))
        content.addView(text("각 문항에 대해 0~10 중 하나를 선택해주세요.", 13f, gray).apply {
            setPadding(0, dp(4), 0, dp(16))
        })

        // 대상자
        content.addView(text("대상자", 15f, navy, bold = true))
        etParticipant = EditText(this).apply {
            hint = "예: P01"
            setText(PilotPrefs.getCurrentParticipant(this@NasaTlxActivity))
            setTextColor(navy)
        }
        content.addView(etParticipant)

        // 개입 조건
        content.addView(text("개입 조건", 15f, navy, bold = true).apply { setPadding(0, dp(12), 0, 0) })
        rgCondition = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val lastCondition = PilotPrefs.getAll(this).lastOrNull()?.condition
        conditions.forEach { c ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = c
                tag = c
                setTextColor(navy)
            }
            rgCondition.addView(rb)
            if (c == lastCondition) rb.isChecked = true
        }
        content.addView(rgCondition)

        // 6개 문항
        items.forEachIndexed { idx, item -> content.addView(buildItem(idx, item)) }

        content.addView(Button(this).apply {
            text = "저장"
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_button_blue)
            backgroundTintList = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(24)
            }
            setOnClickListener { submit() }
        })

        onBackPressedDispatcher.addCallback(this, backCallback)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(content)
        })
    }

    private fun buildItem(idx: Int, item: Item): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        box.addView(text("${idx + 1}. ${item.title}", 15f, navy, bold = true))
        box.addView(text(item.question, 14f, navy).apply { setPadding(0, dp(4), 0, dp(8)) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cells = (0..10).map { v ->
            TextView(this).apply {
                text = v.toString()
                gravity = Gravity.CENTER
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    marginStart = dp(1); marginEnd = dp(1)
                }
                setOnClickListener {
                    scores[idx] = v
                    paintCells(idx)
                }
            }
        }
        cells.forEach { row.addView(it) }
        scaleCells.add(cells)
        paintCells(idx)
        box.addView(row)

        val anchors = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        anchors.addView(text("0 = ${item.low}", 12f, gray).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        anchors.addView(text("10 = ${item.high}", 12f, gray).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.END
        })
        box.addView(anchors)
        return box
    }

    private fun paintCells(idx: Int) {
        scaleCells.getOrNull(idx)?.forEachIndexed { v, cell ->
            val selected = scores[idx] == v
            cell.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                if (selected) setColor(blue) else {
                    setColor(0xFFF4F6F8.toInt())
                    setStroke(dp(1), 0xFFDDDDDD.toInt())
                }
            }
            cell.setTextColor(if (selected) 0xFFFFFFFF.toInt() else navy)
            cell.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun submit() {
        val participant = etParticipant.text.toString().trim()
        val condition = rgCondition.findViewById<RadioButton>(rgCondition.checkedRadioButtonId)?.tag as? String
        val missing = scores.indices.filter { scores[it] < 0 }.map { it + 1 }

        val error = when {
            participant.isEmpty() -> "대상자를 입력해주세요."
            condition == null -> "개입 조건을 선택해주세요."
            missing.isNotEmpty() -> "${missing.joinToString(", ")}번 문항을 선택해주세요."
            else -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }

        TlxPrefs.add(
            this,
            TlxPrefs.Record(TlxPrefs.newId(), participant, condition!!, System.currentTimeMillis(), scores.toList())
        )
        Toast.makeText(this, "NASA-TLX 응답을 저장했습니다. ($participant / $condition)", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** 응답을 하나라도 고른 상태에서 뒤로 가면 확인 후 나간다. */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (scores.none { it >= 0 }) {
                finish()
                return
            }
            AlertDialog.Builder(this@NasaTlxActivity)
                .setMessage("저장하지 않고 나갈까요? 입력한 응답은 사라집니다.")
                .setPositiveButton("나가기") { _, _ -> finish() }
                .setNegativeButton("계속 작성", null)
                .show()
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
