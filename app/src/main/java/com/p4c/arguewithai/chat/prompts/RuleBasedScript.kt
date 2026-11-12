package com.p4c.arguewithai.chat.prompts

/**
 * RuleBasedChatbotActivity가 진행할 대화 스크립트.
 * index 0..maxIndex-1 은 사용자 입력을 받는 질문, maxIndex 는 종료 메시지.
 */
interface RuleBasedScript {
    val maxIndex: Int
    fun getPrompt(index: Int): String
    fun isValidMessage(index: Int, text: String): Boolean
    fun onMessageAccepted(index: Int, text: String) {}
}

/** 기존 Self-Affirmation 스크립트(AffirmationPrompts)를 RuleBasedScript로 감싼 것 */
class AffirmationScript : RuleBasedScript {
    private var selectedKeyword: String? = null

    override val maxIndex: Int = AffirmationPrompts.maxIndex

    override fun getPrompt(index: Int): String = AffirmationPrompts.getPrompt(index)

    override fun isValidMessage(index: Int, text: String): Boolean =
        AffirmationPrompts.isValidMessage(index, text, selectedKeyword)

    override fun onMessageAccepted(index: Int, text: String) {
        if (index == 0) {
            selectedKeyword = text.replace("\\s".toRegex(), "").removeSuffix(".")
        }
    }
}
