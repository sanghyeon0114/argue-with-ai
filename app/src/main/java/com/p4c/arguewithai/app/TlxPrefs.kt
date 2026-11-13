package com.p4c.arguewithai.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 파일럿 테스트용 NASA-TLX 응답 저장(기기 로컬).
 * 6개 항목 모두 0~10. 수행도(Performance)는 0 = 완벽함, 10 = 실패 이므로
 * 6개 점수를 그대로 평균하면 Raw TLX(작업 부하, 높을수록 부담 큼)가 된다.
 */
object TlxPrefs {
    private const val PREFS = "argue_prefs"
    private const val KEY_RECORDS = "tlx_records"

    /** 항목 순서: 정신적 요구, 신체적 요구, 시간적 요구, 수행도, 노력, 좌절감 */
    val ITEM_KEYS = listOf("mental", "physical", "temporal", "performance", "effort", "frustration")

    data class Record(
        val id: String,
        val participant: String,
        val condition: String,   // baseline / affirmation / justification
        val submittedAt: Long,
        val scores: List<Int>    // ITEM_KEYS 순서, 각 0~10
    ) {
        val rawTlx: Double get() = scores.average()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun add(context: Context, record: Record) {
        save(context, getAll(context) + record)
    }

    /** 선택한 id 의 응답들을 지우고, 지운 개수를 돌려준다. */
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
            Record(
                id = o.optString("id"),
                participant = o.optString("participant"),
                condition = o.optString("condition"),
                submittedAt = o.optLong("submittedAt"),
                scores = ITEM_KEYS.map { o.optInt(it) }
            )
        }
    }

    private fun save(context: Context, records: List<Record>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("participant", r.participant)
                put("condition", r.condition)
                put("submittedAt", r.submittedAt)
                ITEM_KEYS.forEachIndexed { i, key -> put(key, r.scores[i]) }
            })
        }
        prefs(context).edit { putString(KEY_RECORDS, arr.toString()) }
    }
}
