package com.p4c.arguewithai.repository.intervention

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
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
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
            "updatedAtMs" to time.dayUTC(ms)
        )

        payload["message"] = msg.text

        chatMessagesCol(msg.sessionId)
            .document(docId)
            .set(payload, SetOptions.merge())
            .await()
    }
}