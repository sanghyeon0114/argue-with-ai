package com.p4c.arguewithai.chat.prompts

import android.content.Context
import com.p4c.arguewithai.utils.Logger
import org.json.JSONArray

/**
 * Firebase AI 호출이 실패(에러·시간 초과·응답 해석 실패)했을 때 쓰는 대체 질문.
 * tools/generate_fallback_questions.py 로 미리 생성해 둔 assets 의 질문 중 하나를 무작위로 고른다.
 *
 *  stage 0 → q1.json : 앱 실행 의도 확인 (첫 질문)
 *  stage 1 → q2.json : 의미성 인식
 *  stage 2 → q3.json : 후회 및 감정 인식
 */
object FallbackQuestions {
    private val FILES = listOf("q1.json", "q2.json", "q3.json")

    /** assets 파일을 읽지 못했을 때 쓰는 최후의 기본 문장 */
    private val DEFAULTS = listOf(
        "10분째 영상을 보고 있어서 잠깐 말 걸어 봤어요. 재밌는 영상이 계속 나오면 시간 가는 줄 모르게 되잖아요. 처음에 어떤 마음으로 앱을 켰나요?",
        "이야기해 줘서 고마워요. 그 마음이 어땠는지 조금은 알 것 같아요. 방금 보낸 10분은 어떤 시간이었나요?",
        "차분히 돌아봐 줘서 고마워요. 이렇게 생각해 보면 여러 마음이 들기도 해요. 지금 영상 본 시간을 떠올리면 만족스러운가요, 아쉬운가요?",
    )

    /** 마지막 단계(stage 2)를 통과했는데 AI 가 마무리 인사를 만들지 못했을 때 쓰는 문장 */
    const val CLOSING = "솔직하게 이야기해 줘서 고마워요. 이렇게 잠깐 돌아보는 것만으로도 의미가 있어요. 이제 계속 볼지는 편하게 정하면 돼요."

    private val cache = arrayOfNulls<List<String>>(FILES.size)

    fun random(context: Context, stage: Int): String {
        val idx = stage.coerceIn(0, FILES.lastIndex)
        return load(context, idx).randomOrNull() ?: DEFAULTS[idx]
    }

    @Synchronized
    private fun load(context: Context, idx: Int): List<String> {
        cache[idx]?.let { return it }
        val list = runCatching {
            context.assets.open(FILES[idx]).bufferedReader(Charsets.UTF_8).use { reader ->
                val arr = JSONArray(reader.readText())
                List(arr.length()) { arr.getString(it).trim() }.filter { it.isNotEmpty() }
            }
        }.onFailure { Logger.e("[FallbackQuestions] failed to load ${FILES[idx]}", it) }
            .getOrDefault(emptyList())
        cache[idx] = list
        return list
    }
}
