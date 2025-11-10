package com.example.arguewithai.firebase

import android.os.Build
import com.example.arguewithai.utils.SystemTimeProvider
import com.example.arguewithai.utils.TimeProvider
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
        userRoot()
            .collection(FirebaseConfig.User.CHAT)
            .document(sessionId)

    private fun chatMessagesCol(sessionId: String) =
        chatSessionDoc(sessionId).collection(FirebaseConfig.User.Chat.MESSAGES)

    suspend fun appendMessage(msg: ChatMessage, order: Int) {
        val docId = order.toString()
        val ms = time.nowMs()

        val payload = hashMapOf(
            "updatedAt" to ms,
            "updatedAtMs" to time.dayUTC(ms)
        )

        if (msg.sender == Sender.AI) {
            payload["question"] = msg.text
            payload["order"] = order
        } else if (msg.sender == Sender.USER) {
            payload["answer"] = msg.text
        }

        chatMessagesCol(msg.sessionId)
            .document(docId)
            .set(payload, SetOptions.merge())
            .await()
    }

}
