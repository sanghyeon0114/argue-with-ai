package com.p4c.arguewithai.platform.ai

import com.google.firebase.ai.type.Schema as FbSchema

object ChatContract {
    const val FIELD_TEXT = "text"
    const val FIELD_SCORE = "score"

    data class Type(
        val text: String,
        val score: Int
    )

    val schema: FbSchema = FbSchema.obj(
        mapOf(
            FIELD_TEXT to FbSchema.string(),
            FIELD_SCORE to FbSchema.integer(
                minimum = -3.0,
                maximum = 3.0
            )
        )
    )
}