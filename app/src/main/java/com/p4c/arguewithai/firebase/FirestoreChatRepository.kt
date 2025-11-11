package com.p4c.arguewithai.firebase

import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

enum class Sender {
    AI,
    USER,
    NONE
}

enum class ExitMethod { BUTTON, NAV_BAR }

data class ChatMessage(
    val sessionId: String = "",
    val sender: Sender = Sender.NONE,
    val text: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val createdAt: Timestamp? = null
)

class FirestoreChatRepository(
    private val time: TimeProvider = SystemTimeProvider()
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
    private fun userRoot() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    private fun chatSessionDoc(sessionId: String) =
        userRoot().collection(FirebaseConfig.User.CHAT).document(sessionId)

    private fun chatMessagesCol(sessionId: String) =
        chatSessionDoc(sessionId).collection(FirebaseConfig.User.Chat.MESSAGES)

    private fun chatExitCol(sessionId: String) =
        chatSessionDoc(sessionId).collection(FirebaseConfig.User.Chat.EXIT)

    suspend fun appendMessage(msg: ChatMessage, order: Int) {
        val docId = order.toString()
        val ms = time.nowMs()

        val payload = hashMapOf(
            "updatedAt" to ms,
            "updatedAtMs" to time.dayUTC(ms)
        )

        when (msg.sender) {
            Sender.AI -> {
                payload["question"] = msg.text
                payload["order"] = order
            }
            Sender.USER -> {
                payload["answer"] = msg.text
            }
            else -> Unit
        }

        chatMessagesCol(msg.sessionId)
            .document(docId)
            .set(payload, SetOptions.merge())
            .await()
    }

    suspend fun logExit(
        sessionId: String,
        finished: Boolean,
        method: ExitMethod,
        lastOrder: Int? = null,
        note: String? = null
    ) {
        val ms = time.nowMs()
        val data = hashMapOf(
            "finished" to finished,
            "method" to method.name,
            "note" to note,
            "atMs" to ms,
            "at" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        )

        chatExitCol(sessionId).document(ms.toString()).set(data).await()
    }
}