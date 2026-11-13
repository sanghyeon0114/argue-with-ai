package com.p4c.arguewithai.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 파일럿 테스트 모드 on/off 와 측정 기록(기기 로컬 저장).
 * 기록 1건 = 개입 1회: 개입 화면이 뜬 시점 ~ 닫힌 시점까지의 시간 + 사용자가 보낸 메시지의 타이핑 수.
 * 각 기록에는 어떤 대상자에게 시행했는지 메모(memo)를 남길 수 있다.
 */
object PilotPrefs {
    private const val PREFS = "argue_prefs"
    private const val KEY_ENABLED = "pilot_mode_enabled"
    private const val KEY_RECORDS = "pilot_records"
    private const val KEY_CURRENT_PARTICIPANT = "pilot_current_participant"

    data class Record(
        val id: String,
        val condition: String,   // baseline / affirmation / justification
        val startedAt: Long,     // 개입 시작 시각 (epoch ms)
        val durationMs: Long,    // 개입이 켜진 시간 ~ 꺼진 시간
        val keystrokes: Int,     // 사용자가 보낸 메시지 전체의 타이핑 수
        val messages: Int,       // 사용자가 보낸 메시지 수
        val reason: String,      // 종료 사유 (final_ack = 정상 완료)
        val memo: String = ""    // 대상자 메모
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /** 새 기록에 자동으로 붙는 대상자 메모 */
    fun getCurrentParticipant(context: Context): String =
        prefs(context).getString(KEY_CURRENT_PARTICIPANT, "").orEmpty()

    fun setCurrentParticipant(context: Context, memo: String) {
        prefs(context).edit { putString(KEY_CURRENT_PARTICIPANT, memo.trim()) }
    }

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun add(context: Context, record: Record) {
        save(context, getAll(context) + record)
    }

    @Synchronized
    fun updateMemo(context: Context, id: String, memo: String) {
        save(context, getAll(context).map { if (it.id == id) it.copy(memo = memo.trim()) else it })
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        save(context, getAll(context).filterNot { it.id == id })
    }

    /** 선택한 id 의 기록들을 지우고, 지운 개수를 돌려준다. */
    @Synchronized
    fun deleteAll(context: Context, ids: Set<String>): Int {
        val all = getAll(context)
        val kept = all.filterNot { it.id in ids }
        save(context, kept)
        return all.size - kept.size
    }

    fun getAll(context: Context): List<Record> {
        val arr = runCatching { JSONArray(prefs(context).getString(KEY_RECORDS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val startedAt = o.optLong("startedAt")
            Record(
                id = o.optString("id").ifEmpty { "legacy-$startedAt-$i" },
                condition = o.optString("condition"),
                startedAt = startedAt,
                durationMs = o.optLong("durationMs"),
                keystrokes = o.optInt("keystrokes"),
                messages = o.optInt("messages"),
                reason = o.optString("reason"),
                memo = o.optString("memo")
            )
        }
    }

    private fun save(context: Context, records: List<Record>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("condition", r.condition)
                put("startedAt", r.startedAt)
                put("durationMs", r.durationMs)
                put("keystrokes", r.keystrokes)
                put("messages", r.messages)
                put("reason", r.reason)
                put("memo", r.memo)
            })
        }
        prefs(context).edit { putString(KEY_RECORDS, arr.toString()) }
    }
}
