package com.p4c.arguewithai.repository.intervention

import com.p4c.arguewithai.repository.ParticipantId
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.p4c.arguewithai.repository.FirebaseConfig
import kotlinx.coroutines.tasks.await

data class BlockingMessage(
    val sessionId: String = "",
    val text: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val createdAt: Timestamp? = null
)

class FirestoreBlockingRepository(
    private val time: TimeProvider = SystemTimeProvider()
) : RuleBasedChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    /** 문서 ID = 참여자 ID (P1~PN) */
    private fun uid(): String = ParticipantId.current()
    private fun userRoot() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    private fun chatSessionDoc(sessionId: String) =
        userRoot().collection(FirebaseConfig.User.BLOCKING).document(sessionId)

    private fun chatMessagesCol(sessionId: String) =
        chatSessionDoc(sessionId).collection(FirebaseConfig.User.Blocking.MESSAGES)

    suspend fun updateMessage(msg: BlockingMessage, order: Int) {
        val docId = order.toString()
        val ms = time.nowMs()

        val payload = hashMapOf<String, Any>(
            "updatedAt" to ms,
            "updatedAtMs" to time.dayUTC(ms),
            "message" to msg.text
        )

        chatMessagesCol(msg.sessionId)
            .document(docId)
            .set(payload, SetOptions.merge())
            .await()
    }
    /** Number Input Task(RuleBasedChatbotActivity) 대화 로그: question / answer */
    override suspend fun updateMessage(sessionId: String, sender: Sender, text: String, order: Int) {
        val ms = time.nowMs()
        val payload = hashMapOf<String, Any>(
            "updatedAt" to ms,
            "updatedAtMs" to time.dayUTC(ms)
        )
        when (sender) {
            Sender.CHATBOT -> payload["question"] = text
            Sender.USER -> payload["answer"] = text
            else -> Unit
        }
        chatMessagesCol(sessionId)
            .document(order.toString())
            .set(payload, SetOptions.merge())
            .await()
    }

    override suspend fun logStart(sessionId: String) {
        val ms = time.nowMs()
        val data = mapOf(
            "start" to mapOf(
                "atMs" to ms,
                "at" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
            )
        )
        chatSessionDoc(sessionId).set(data, SetOptions.merge()).await()
    }
    override suspend fun logExit(
        sessionId: String,
        finished: Boolean,
        method: ExitMethod,
        note: String?
    ) {
        val ms = time.nowMs()
        val data = mapOf(
            "exit" to mapOf(
                "finished" to finished,
                "method" to method.name,
                "note" to note,
                "atMs" to ms,
                "at" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
            )
        )

        chatSessionDoc(sessionId).set(data, SetOptions.merge()).await()
    }
}