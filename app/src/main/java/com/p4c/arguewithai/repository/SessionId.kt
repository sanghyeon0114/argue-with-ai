package com.p4c.arguewithai.repository

/**
 * @param week 세션이 시작된 실험 주차(1/2/3). Firestore 경로(instagram_sessions/weekN/sessions/...)에 쓰이며,
 *             세션 도중 주차가 바뀌어도 시작 시점의 주차 폴더에 끝까지 기록되도록 ID 에 함께 보관한다.
 */
data class SessionId(val value: String, val app: String = "", val week: Int = 1)
