package com.p4c.arguewithai.chat.prompts

object AffirmationPrompts {
    val keyword: List<String> = listOf("자제력", "건강", "질서", "끈기", "자기인식", "자기관리", "책임감")
    val templateValue: List<String> = listOf(
        "나는 ____을(를) 중요하게 생각합니다.",
        "____는 나에게 중요합니다.",
        "나는 ____이(가) 중요하다고 생각합니다.",
        "나는 내가 ____을(를) 중요하게 여기는 사람이라고 생각합니다."
    )

    val templateAction: List<String> = listOf(
        "나는 지금 스마트폰 사용을 멈추고 ____을(를) 하겠습니다.",
        "나는 ____을(를) 하기 위해 스마트폰을 덜 사용할 수 있습니다.",
        "나는 ____을(를) 하기 위해 앱을 종료할 수 있습니다.",
        "나는 ____을(를) 하기 위해 화면을 끌 수 있습니다."
    )

    val basePrompts: List<String> = listOf(
        "자제력, 건강, 질서, 끈기, 자기 인식, 자기 관리, 책임감 중에 하나를 선택해주세요.",
        "아래 문장을 직접 완성해주세요.\n",
        "아래 문장을 직접 완성해주세요.\n",
        "감사합니다. 앱을 종료하겠습니다."
    )

    val maxIndex: Int = basePrompts.lastIndex

    fun getPrompt(index: Int): String {
        return when (index) {
            0 -> basePrompts[0]
            1 -> basePrompts[1] + templateValue.random()
            2 -> basePrompts[2] + templateAction.random()
            else -> basePrompts.getOrNull(index) ?: ""
        }
    }

    fun isValidMessage(index: Int, text: String, savedKeyword: String?): Boolean {
        val cleanText = text.replace("\\s".toRegex(), "")

        return when (index) {
            0 -> {
                val selectedKeyword = cleanText.removeSuffix(".")
                keyword.contains(selectedKeyword)
            }
            1 -> {
                // templateValue의 4가지 경우의 수를 모두 커버하는 정규식 리스트
                val patterns = listOf(
                    "^나는.+[을를]중요하게생각합니다\\.?$".toRegex(),
                    "^.+[은는]나에게중요합니다\\.?$".toRegex(),
                    "^나는.+[이가]중요하다고생각합니다\\.?$".toRegex(),
                    "^나는내가.+[을를]중요하게여기는사람이라고생각합니다\\.?$".toRegex()
                )
                // 입력값이 정규식 중 하나라도 일치하는지 확인
                val matchesFormat = patterns.any { it.matches(cleanText) }
                // 이전에 저장한 키워드가 문장에 포함되어 있는지 확인
                val containsKeyword = savedKeyword != null && cleanText.contains(savedKeyword)

                matchesFormat && containsKeyword
            }
            2 -> {
                // templateAction의 4가지 경우의 수를 모두 커버하는 정규식 리스트
                val patterns = listOf(
                    "^나는지금스마트폰사용을멈추고.+[을를]하겠습니다\\.?$".toRegex(),
                    "^나는.+[을를]하기위해스마트폰을덜사용할수있습니다\\.?$".toRegex(),
                    "^나는.+[을를]하기위해앱을종료할수있습니다\\.?$".toRegex(),
                    "^나는.+[을를]하기위해화면을끌수있습니다\\.?$".toRegex()
                )
                patterns.any { it.matches(cleanText) }
            }
            else -> true
        }
    }
}