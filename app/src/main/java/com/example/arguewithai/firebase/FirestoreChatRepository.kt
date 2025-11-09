package com.example.arguewithai.firebase

import android.os.Build
import com.example.arguewithai.utils.SystemTimeProvider
import com.example.arguewithai.utils.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date

data class ChatMessage(
    val sessionId: String = "",
    val sender: String = "",     // "user" | "ai"
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

    // ------- 경로 빌더 -------
    private fun userRoot() =
        db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    private fun chatSessionDoc(sessionId: String) =
        userRoot()
            .collection(FirebaseConfig.User.CHAT)
            .document(sessionId)

    private fun chatMessagesCol(sessionId: String) =
        chatSessionDoc(sessionId).collection("messages")

    fun upsertSessionMeta(
        sessionId: String,
        title: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        val now = Timestamp(Date(time.nowMs()))
        val doc = chatSessionDoc(sessionId)

        val data = hashMapOf(
            "sessionId" to sessionId,
            "title" to (title ?: FieldValue.delete()),
            "updatedAt" to now,
            "device" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "sdkInt" to Build.VERSION.SDK_INT
            )
        )

        doc.set(
            mapOf(
                "startedAt" to FieldValue.serverTimestamp(),
                "startedAtMs" to time.nowMs()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            doc.set(data, SetOptions.merge())
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        }.addOnFailureListener { onResult(false) }
    }

    fun appendMessage(
        sessionId: String,
        sender: String,
        text: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        // 세션 메타 업데이트(없으면 만들어짐)
        upsertSessionMeta(sessionId) { ok ->
            if (!ok) {
                onResult(false)
                return@upsertSessionMeta
            }

            val data = mapOf(
                "sessionId" to sessionId,
                "sender" to sender,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
                "createdAtMs" to time.nowMs()
            )

            chatMessagesCol(sessionId)
                .add(data)
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        }
    }

    suspend fun appendMessage(msg: ChatMessage) {
        // 세션 메타 먼저 병합
        chatSessionDoc(msg.sessionId).set(
            mapOf(
                "sessionId" to msg.sessionId,
                "updatedAt" to Timestamp(Date(time.nowMs())),
                "device" to mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdkInt" to Build.VERSION.SDK_INT
                )
            ),
            SetOptions.merge()
        ).await()

        val payload = hashMapOf(
            "sessionId" to msg.sessionId,
            "sender" to msg.sender,
            "text" to msg.text,
            "createdAt" to FieldValue.serverTimestamp(),
            "createdAtMs" to msg.createdAtMs
        )
        chatMessagesCol(msg.sessionId).add(payload).await()
    }
}
