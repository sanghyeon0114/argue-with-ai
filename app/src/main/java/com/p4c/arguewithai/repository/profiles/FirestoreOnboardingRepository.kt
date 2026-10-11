package com.p4c.arguewithai.repository.profiles

import com.p4c.arguewithai.repository.ParticipantId
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.p4c.arguewithai.repository.FirebaseConfig
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * 온보딩 단계별 데이터를 users/{참여자 ID} 문서에 저장한다.
 *
 * 모든 쓰기는 다음 순서로 진행한다.
 *  1. 서버(캐시 X)에서 문서를 읽어 기존 데이터 존재 여부 확인
 *  2. 문서가 없으면 set 으로 새로 생성, 있으면 update 로 해당 필드만 수정
 *  3. 서버에서 다시 읽어 저장한 값이 실제로 반영됐는지 검증
 *
 * 필드는 "userInfo.name" 처럼 점(.)으로 구분한 경로로 지정한다.
 */
class FirestoreOnboardingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    enum class WriteMode { CREATED, UPDATED }

    data class Outcome(val mode: WriteMode, val verified: Boolean)

    /** 문서 ID = 참여자 ID (P1~PN) */
    private fun uid(): String = ParticipantId.current()

    private fun userDoc() = db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    /** 서버에서 users/{참여자 ID} 문서를 읽는다. 문서가 없으면 null. */
    suspend fun fetchFromServer(): DocumentSnapshot? = fetchFromServer(uid())

    /** 지정한 참여자 ID 문서를 서버에서 읽는다. 문서가 없으면 null. */
    suspend fun fetchFromServer(participantId: String): DocumentSnapshot? =
        db.collection(FirebaseConfig.ROOT_COLLECTION).document(participantId).get(Source.SERVER).await().takeIf { it.exists() }

    /**
     * @param fields   저장할 값 (점 경로 → 값). 검증 대상이기도 하다.
     * @param section  updatedAt 을 기록할 상위 필드 (예: "userInfo")
     * @param extra    검증하지 않고 함께 저장할 값 (예: 기기 정보)
     */
    suspend fun upsertAndVerify(
        fields: Map<String, Any>,
        section: String,
        extra: Map<String, Any> = emptyMap()
    ): Outcome {
        val doc = userDoc()
        val existing = doc.get(Source.SERVER).await()
        val all = fields + extra + ("$section.updatedAt" to Timestamp(Date()))

        val mode = if (existing.exists()) {
            // 기존 문서: 지정한 필드만 수정 (같은 상위 맵의 다른 필드는 유지)
            doc.update(all).await()
            WriteMode.UPDATED
        } else {
            // 문서 없음: 기본 정보와 함께 새로 생성
            val created = nest(all) + mapOf(
                "participantId" to uid(),
                "authUid" to (auth.currentUser?.uid ?: ""),
                "createdAt" to Timestamp(Date())
            )
            doc.set(created).await()
            WriteMode.CREATED
        }

        val after = doc.get(Source.SERVER).await()
        val verified = fields.all { (path, expected) -> sameValue(after.get(path), expected) }
        return Outcome(mode, verified)
    }

    private fun sameValue(actual: Any?, expected: Any): Boolean = when (expected) {
        is Int, is Long -> (actual as? Number)?.toLong() == (expected as Number).toLong()
        else -> actual == expected
    }

    /** {"a.b": 1, "a.c": 2} → {"a": {"b": 1, "c": 2}} */
    @Suppress("UNCHECKED_CAST")
    private fun nest(flat: Map<String, Any>): Map<String, Any> {
        val root = mutableMapOf<String, Any>()
        for ((path, value) in flat) {
            val keys = path.split(".")
            var cur = root
            for (k in keys.dropLast(1)) {
                cur = cur.getOrPut(k) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
            }
            cur[keys.last()] = value
        }
        return root
    }

    companion object {
        const val NAME = "userInfo.name"
        const val OVERLAY = "onboarding.overlayGranted"
        const val ACCESSIBILITY = "accessibility.enabled"
        const val PIP = "onboarding.pipConfirmed"
        const val TYPE = "intervention.type"
        const val COMPLETED = "onboarding.completed"
    }
}
