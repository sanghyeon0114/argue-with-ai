package com.p4c.arguewithai.chat.prompts

import com.p4c.arguewithai.utils.KeystrokeCounter
import kotlin.random.Random

/**
 * Baseline: Number Input Task (성찰 없는 타이핑)
 *
 * 1~9 숫자를 고유어(하나~아홉)로 무작위 나열해 제시하고, 그대로 따라 입력하게 한다.
 * 3번 질문(라운드)으로 진행하며, 라운드마다 정확히 [ROUND_KEYSTROKES]타(30타),
 * 3라운드 합계 정확히 [TARGET_KEYSTROKES]타(90타)가 되도록 세션마다 수열을 새로 생성한다.
 *
 * 타이핑 수 = 두벌식 키 입력 수(자모 1개 = 1타, 겹모음/겹받침 = 2타) + 표시된 띄어쓰기 수.
 * (Self-Affirmation 문장도 같은 방식으로 세면 최소 89타)
 */
class NumberInputScript(private val random: Random = Random.Default) : RuleBasedScript {

    companion object {
        val KOREAN_DIGITS: List<String> = listOf("하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉")
        const val ROUNDS = 3
        val DIGITS_PER_ROUND: IntRange = 5..6
        /** 3라운드 합계 타이핑 수 */
        const val TARGET_KEYSTROKES = 90
        /** 라운드별 타이핑 수 (TARGET_KEYSTROKES / ROUNDS) */
        const val ROUND_KEYSTROKES = TARGET_KEYSTROKES / ROUNDS

        private const val FIRST_PROMPT = "아래 글자를 그대로 따라 입력해주세요.\n\n"
        private const val NEXT_PROMPT = "다음 글자를 그대로 따라 입력해주세요.\n\n"
        private const val FINAL_PROMPT = "감사합니다. 앱을 종료하겠습니다."

        fun countKeystrokes(text: String): Int = KeystrokeCounter.count(text)

        private fun normalize(text: String) = text.replace("\\s".toRegex(), "")
    }

    /** 이번 세션에 제시할 3개 수열 (예: "다섯 둘 여덟 하나 아홉 셋") */
    val sequences: List<String> = generateSequences()

    val totalKeystrokes: Int = sequences.sumOf { countKeystrokes(it) }

    override val maxIndex: Int = ROUNDS

    override fun getPrompt(index: Int): String = when (index) {
        0 -> FIRST_PROMPT + sequences[0]
        in 1 until ROUNDS -> NEXT_PROMPT + sequences[index]
        else -> FINAL_PROMPT
    }

    override fun isValidMessage(index: Int, text: String): Boolean {
        val target = sequences.getOrNull(index) ?: return true
        return normalize(text) == normalize(target)
    }

    // 라운드마다 정확히 ROUND_KEYSTROKES타이므로 합계는 항상 TARGET_KEYSTROKES타
    private fun generateSequences(): List<String> = List(ROUNDS) { generateRound() }

    private fun generateRound(): String {
        while (true) {
            val length = DIGITS_PER_ROUND.random(random)
            val round = List(length) { KOREAN_DIGITS.random(random) }.joinToString(" ")
            if (countKeystrokes(round) == ROUND_KEYSTROKES) return round
        }
    }
}
