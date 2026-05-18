package com.p4c.arguewithai.repository.intervention

import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.p4c.arguewithai.repository.FirebaseConfig
import kotlinx.coroutines.tasks.await

data class AffirmationMessage(
    val sessionId: String = "",
    val sender: Sender = Sender.NONE,
    val text: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val createdAt: Timestamp? = null
)

class FirestoreAffirmationRepository(
    private val time: TimeProvider = SystemTimeProvider()
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
    private fun userRoot() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    private fun chatSessionDoc(sessionId: String) =
        userRoot().collection(FirebaseConfig.User.AFFIRMATION).document(sessionId)

    private fun chatMessagesCol(sessionId: String) =
        chatSessionDoc(sessionId).collection(FirebaseConfig.User.Affirmation.MESSAGES)

    suspend fun updateMessage(msg: AffirmationMessage, order: Int) {
        val docId = order.toString()
        val ms = time.nowMs()

        val payload = hashMapOf<String, Any>(
            "updatedAt" to ms,
            "updatedAtMs" to time.dayUTC(ms)
        )

        when (msg.sender) {
            Sender.CHATBOT -> payload["question"] = msg.text
            Sender.USER -> payload["answer"] = msg.text
            else -> Unit
        }

        chatMessagesCol(msg.sessionId)
            .document(docId)
            .set(payload, SetOptions.merge())
            .await()
    }
    suspend fun logStart(sessionId: String) {
        val ms = time.nowMs()
        val data = mapOf(
            "start" to mapOf(
                "atMs" to ms,
                "at" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
            )
        )
        chatSessionDoc(sessionId).set(data, SetOptions.merge()).await()
    }
    suspend fun logExit(
        sessionId: String,
        finished: Boolean,
        method: ExitMethod,
        note: String? = null
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