package com.p4c.arguewithai.platform.ai

import com.google.firebase.ai.type.Schema as FbSchema

object ChatContract {
    const val FIELD_TEXT = "text"
    const val FIELD_SCORE = "score"

    data class Type(
        val text: String,
        val score: Boolean
    )

    val schema: FbSchema = FbSchema.obj(
        mapOf(
            FIELD_TEXT to FbSchema.string(description = "사용자에게 줄 대답"),
            FIELD_SCORE to FbSchema.boolean(description = "사용자의 답변이 정답/논리적인지 여부")
        )
    )
}