package com.p4c.arguewithai.platform.ai

import com.google.firebase.ai.type.Schema

object Schema {
    val chatSchema: Schema = Schema.obj(
        mapOf(
            "text" to Schema.string(),
            "score" to Schema.integer(minimum = -3.0, maximum = 3.0)
        )
    )
}