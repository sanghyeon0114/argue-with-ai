package com.p4c.arguewithai.repository

import com.p4c.arguewithai.utils.TimeProvider
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

@JvmInline
value class SessionId(val value: String)

data class ShortformSession(
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val durationSec: Long? = null,
    val startEpoch: Long? = null,
    val endEpoch: Long? = null,
    val day: String? = null,
    val app: String? = null
) {
    companion object Fields {
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
        const val DURATION_SEC = "durationSec"
        const val START_EPOCH = "startEpoch"
        const val END_EPOCH = "endEpoch"
        const val DAY = "day"
        const val APP = "app"
    }
}

interface SessionRepository {
    suspend fun startSession(app: String): SessionId
    suspend fun endSession(sessionId: SessionId): ShortformSession
}



class FirestoreSessionRepository (
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val time: TimeProvider = SystemTimeProvider()
) : SessionRepository {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
    private fun sessionsCollection() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid()).collection(FirebaseConfig.User.SESSIONS)

    override suspend fun startSession(app: String): SessionId {
        val startMs = time.nowMs()

        val data = hashMapOf(
            ShortformSession.START_TIME to Timestamp(Date(startMs)),
            ShortformSession.END_TIME to null,
            ShortformSession.DURATION_SEC to null,
            ShortformSession.START_EPOCH to startMs,
            ShortformSession.DAY to time.dayUTC(startMs),
            ShortformSession.APP to app
        )

        val ref = sessionsCollection().add(data).await()
        return SessionId(ref.id)
    }

    override suspend fun endSession(sessionId: SessionId): ShortformSession {
        val endMs = time.nowMs()
        val ref = sessionsCollection().document(sessionId.value)

        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw NoSuchElementException("Session not found: ${sessionId.value}")

            val startMs = snap.getLong(ShortformSession.START_EPOCH) ?: endMs
            val duration = ((endMs - startMs) / 1000).coerceAtLeast(0)

            tx.update(
                ref,
                mapOf(
                    ShortformSession.END_TIME to Timestamp(Date(endMs)),
                    ShortformSession.END_EPOCH to endMs,
                    ShortformSession.DURATION_SEC to duration
                )
            )

            ShortformSession(
                startTime = snap.getTimestamp(ShortformSession.START_TIME),
                endTime = Timestamp(Date(endMs)),
                durationSec = duration,
                startEpoch = startMs,
                endEpoch = endMs,
                day = snap.getString(ShortformSession.DAY),
                app = snap.getString(ShortformSession.APP)
            )
        }.await()
    }
}
