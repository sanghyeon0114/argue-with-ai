package com.p4c.arguewithai.repository

import com.p4c.arguewithai.utils.TimeProvider
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

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

data class ScreenUsage(
    val screen: String? = null,
    val durationMs: Long? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val startEpoch: Long? = null,
    val endEpoch: Long? = null,
    val order: Int? = null
) {
    companion object Fields {
        const val SCREEN = "screen"
        const val DURATION_MS = "durationMs"
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
        const val START_EPOCH = "startEpoch"
        const val END_EPOCH = "endEpoch"
        const val ORDER = "order"
    }
}

data class ScreenUsageSummary(
    val screen: String,
    val durationMs: Long,
    val startEpochMs: Long,
    val endEpochMs: Long
)

data class KeyboardUsage(
    val durationMs: Long? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val startEpoch: Long? = null,
    val endEpoch: Long? = null,
    val order: Int? = null
) {
    companion object Fields {
        const val DURATION_MS = "durationMs"
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
        const val START_EPOCH = "startEpoch"
        const val END_EPOCH = "endEpoch"
        const val ORDER = "order"
    }
}

data class KeyboardUsageSummary(
    val durationMs: Long,
    val startEpochMs: Long,
    val endEpochMs: Long
)

interface SessionRepository {
    suspend fun startSession(app: String): SessionId
    suspend fun endSession(sessionId: SessionId): ShortformSession
    suspend fun saveScreenUsage(sessionId: SessionId, screens: List<ScreenUsageSummary>)
    suspend fun saveKeyboardUsage(sessionId: SessionId, keyboardUsages: List<KeyboardUsageSummary>)
}

class FirestoreSessionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val time: TimeProvider = SystemTimeProvider()
) : SessionRepository {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")

    private fun sessionsCollectionName(app: String): String = when (app.lowercase()) {
        "instagram" -> FirebaseConfig.User.INSTAGRAM_SESSIONS
        "youtube" -> FirebaseConfig.User.YOUTUBE_SESSIONS
        else -> FirebaseConfig.User.SESSIONS
    }

    private fun sessionsCollection(app: String) =
        db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid()).collection(sessionsCollectionName(app))

    private fun screensCollection(sessionId: SessionId) =
        sessionsCollection(sessionId.app).document(sessionId.value).collection(FirebaseConfig.User.Sessions.SCREENS)

    private fun keyboardCollection(sessionId: SessionId) =
        sessionsCollection(sessionId.app).document(sessionId.value).collection(FirebaseConfig.User.Sessions.KEYBOARD)

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

        val ref = sessionsCollection(app).add(data).await()
        return SessionId(ref.id, app)
    }

    override suspend fun endSession(sessionId: SessionId): ShortformSession {
        val endMs = time.nowMs()
        val ref = sessionsCollection(sessionId.app).document(sessionId.value)

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

    override suspend fun saveScreenUsage(sessionId: SessionId, screens: List<ScreenUsageSummary>) {
        if (screens.isEmpty()) return

        val col = screensCollection(sessionId)
        val batch = db.batch()
        screens.forEachIndexed { index, s ->
            val order = index + 1
            val ref = col.document(order.toString())
            batch.set(
                ref,
                mapOf(
                    ScreenUsage.SCREEN to s.screen,
                    ScreenUsage.DURATION_MS to s.durationMs,
                    ScreenUsage.START_TIME to Timestamp(Date(s.startEpochMs)),
                    ScreenUsage.END_TIME to Timestamp(Date(s.endEpochMs)),
                    ScreenUsage.START_EPOCH to s.startEpochMs,
                    ScreenUsage.END_EPOCH to s.endEpochMs,
                    ScreenUsage.ORDER to order
                )
            )
        }
        batch.commit().await()
    }

    override suspend fun saveKeyboardUsage(sessionId: SessionId, keyboardUsages: List<KeyboardUsageSummary>) {
        if (keyboardUsages.isEmpty()) return

        val col = keyboardCollection(sessionId)
        val batch = db.batch()
        keyboardUsages.forEachIndexed { index, k ->
            val order = index + 1
            val ref = col.document(order.toString())
            batch.set(
                ref,
                mapOf(
                    KeyboardUsage.DURATION_MS to k.durationMs,
                    KeyboardUsage.START_TIME to Timestamp(Date(k.startEpochMs)),
                    KeyboardUsage.END_TIME to Timestamp(Date(k.endEpochMs)),
                    KeyboardUsage.START_EPOCH to k.startEpochMs,
                    KeyboardUsage.END_EPOCH to k.endEpochMs,
                    KeyboardUsage.ORDER to order
                )
            )
        }
        batch.commit().await()
    }
}
