package com.p4c.arguewithai.platform.ai.scoring

import com.google.firebase.ai.type.Schema

object ScoringContract {
    const val FIELD_SCORE = "score"

    data class Type(
        val score: Boolean
    )

    val schema: Schema = Schema.Companion.obj(
        mapOf(
            FIELD_SCORE to Schema.Companion.boolean(description = "사용자의 대답을 평가")
        )
    )
}