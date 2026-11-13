package com.p4c.arguewithai.utils

/**
 * 타이핑 수 = 두벌식 키 입력 수(자모 1개 = 1타, 겹모음/겹받침 = 2타) + 그 외 문자(띄어쓰기·영문·숫자·기호) 1타.
 * Number Input Task 수열 생성과 파일럿 테스트 측정에서 같은 기준으로 쓴다.
 */
object KeystrokeCounter {
    // 두벌식 기준 키 입력 수가 2인 중성/종성 인덱스
    private val DOUBLE_JUNG = setOf(9, 10, 11, 14, 15, 16, 19)          // ㅘㅙㅚㅝㅞㅟㅢ
    private val DOUBLE_JONG = setOf(3, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18) // ㄳㄵㄶㄺㄻㄼㄽㄾㄿㅀㅄ

    fun count(text: String): Int = text.sumOf { ch ->
        val code = ch.code - 0xAC00
        if (code in 0 until 11172) {
            val jung = (code % 588) / 28
            val jong = code % 28
            1 + (if (jung in DOUBLE_JUNG) 2 else 1) +
                when {
                    jong == 0 -> 0
                    jong in DOUBLE_JONG -> 2
                    else -> 1
                }
        } else {
            1
        }
    }
}
