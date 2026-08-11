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

// 화면 방문 한 건(세그먼트)을 나타낸다. 같은 화면이라도 재방문하면 별도 세그먼트로 취급한다.
data class ScreenUsageSummary(
    val screen: String,
    val durationMs: Long,
    val startEpochMs: Long,
    val endEpochMs: Long
)

interface SessionRepository {
    suspend fun startSession(app: String): SessionId
    suspend fun endSession(sessionId: SessionId): ShortformSession
    suspend fun saveScreenUsage(sessionId: SessionId, screens: List<ScreenUsageSummary>)
}



class FirestoreSessionRepository (
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val time: TimeProvider = SystemTimeProvider()
) : SessionRepository {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
    private fun sessionsCollection() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid()).collection(FirebaseConfig.User.SESSIONS)
    private fun screensCollection(sessionId: SessionId) =
        sessionsCollection().document(sessionId.value).collection(FirebaseConfig.User.Sessions.SCREENS)

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

            // 1초 미만의 세션은 노이즈로 보고 저장하지 않고, 시작 시 만들어둔 문서를 삭제한다.
            if (duration < 1) {
                tx.delete(ref)
            } else {
                tx.update(
                    ref,
                    mapOf(
                        ShortformSession.END_TIME to Timestamp(Date(endMs)),
                        ShortformSession.END_EPOCH to endMs,
                        ShortformSession.DURATION_SEC to duration
                    )
                )
            }

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

    // 세션 동안 머문 화면 방문 기록을 시간 순서 그대로(재방문도 별도 세그먼트로) screens 서브컬렉션에 저장한다.
    override suspend fun saveScreenUsage(sessionId: SessionId, screens: List<ScreenUsageSummary>) {
        if (screens.isEmpty()) return

        val col = screensCollection(sessionId)
        val batch = db.batch()
        screens.forEachIndexed { index, s ->
            val order = index + 1
            val ref = col.document(order.toString()) // 문서 이름을 1, 2, 3... 순서대로 부여
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
}
