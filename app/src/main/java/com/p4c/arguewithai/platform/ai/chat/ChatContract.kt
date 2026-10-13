package com.p4c.arguewithai.platform.ai.chat

import com.google.firebase.ai.type.Schema

object ChatContract {
    const val FIELD_TEXT = "text"

    data class Type(
        val text: String
    )

    val schema: Schema = Schema.Companion.obj(
        mapOf(
            FIELD_TEXT to Schema.Companion.string(description = "사용자에게 줄 대답")
        )
    )
}