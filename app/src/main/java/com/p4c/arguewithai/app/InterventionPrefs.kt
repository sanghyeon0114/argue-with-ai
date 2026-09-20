package com.p4c.arguewithai.app

import android.content.Context
import androidx.core.content.edit

/**
 * 실험 주차(1/2/3)와 개입 여부를 관리한다.
 *  - 1주차: baseline (개입 X)
 *  - 2주차: 개입 단계 (개입 O)
 *  - 3주차: 개입 제거 후 반응 관찰 (개입 X)
 *
 * 개입 여부는 주차에서 파생되며 2주차일 때만 true.
 * MyAccessibilityService 가 KEY("intervention_enabled") 변경을 listen 하고 있으므로
 * 주차 변경 시 두 값을 한 번의 edit 로 같이 기록한다.
 */
object InterventionPrefs {
    private const val PREFS = "argue_prefs"
    private const val KEY = "intervention_enabled"
    private const val KEY_WEEK = "experiment_week"

    const val WEEK_BASELINE = 1
    const val WEEK_INTERVENTION = 2
    const val WEEK_WASHOUT = 3

    fun isValidWeek(week: Int): Boolean = week in WEEK_BASELINE..WEEK_WASHOUT

    fun isEnabledForWeek(week: Int): Boolean = week == WEEK_INTERVENTION

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getWeek(context: Context): Int {
        val p = prefs(context)
        val stored = p.getInt(KEY_WEEK, -1)
        if (isValidWeek(stored)) return stored
        // 주차 도입 이전 버전에서 저장된 값 호환: enabled=true 였다면 2주차로 간주
        return if (p.getBoolean(KEY, false)) WEEK_INTERVENTION else WEEK_BASELINE
    }

    fun setWeek(context: Context, week: Int) {
        require(isValidWeek(week)) { "week must be 1..3 but was $week" }
        prefs(context).edit {
            putInt(KEY_WEEK, week)
            putBoolean(KEY, isEnabledForWeek(week))
        }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY, false)
}
