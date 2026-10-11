package com.p4c.arguewithai.repository

import android.content.Context
import com.p4c.arguewithai.app.OnboardingPrefs

/**
 * 참여자 ID (P1, P2, ...). Firestore 문서 ID 로 사용한다: users/{참여자 ID}
 *
 * 값은 온보딩 1단계에서 입력한 자연수 N 으로 "PN" 형태로 만들어
 * OnboardingPrefs(participant_name)에 저장된다.
 * Application.onCreate 에서 [init] 을 호출해야 Context 없이 어디서든 [current] 를 쓸 수 있다.
 */
object ParticipantId {
    private val PATTERN = Regex("^P[1-9][0-9]*$")

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 자연수 N → "PN". 자연수가 아니면 null. */
    fun fromNumber(n: Int): String? = if (n >= 1) "P$n" else null

    /** "PN" → N. 형식이 아니면 null. */
    fun toNumber(id: String?): Int? =
        id?.takeIf { isValid(it) }?.removePrefix("P")?.toIntOrNull()

    fun isValid(id: String?): Boolean = id != null && PATTERN.matches(id)

    /** 저장된 참여자 ID. 없거나 형식이 잘못됐으면 null. */
    fun currentOrNull(): String? {
        val ctx = appContext ?: return null
        return OnboardingPrefs.getCachedName(ctx)?.takeIf { isValid(it) }
    }

    /** Firestore 문서 ID 로 쓸 참여자 ID. 아직 설정 전이면 예외. */
    fun current(): String =
        currentOrNull() ?: throw IllegalStateException("Participant ID (P1~PN) not set")
}
