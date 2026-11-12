package com.p4c.arguewithai.repository.intervention

/** RuleBasedChatbotActivity가 대화 로그를 남기는 저장소 (조건별로 다른 컬렉션 사용) */
interface RuleBasedChatRepository {
    suspend fun logStart(sessionId: String)
    suspend fun updateMessage(sessionId: String, sender: Sender, text: String, order: Int)
    suspend fun logExit(sessionId: String, finished: Boolean, method: ExitMethod, note: String? = null)
}
