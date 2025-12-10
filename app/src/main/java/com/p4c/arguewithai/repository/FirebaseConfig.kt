package com.p4c.arguewithai.repository

object FirebaseConfig {
    const val ROOT_COLLECTION = "users"

    object User {
        const val SESSIONS = "sessions"
        const val CLIENT = "client"

        object Client {
            const val ACCESSIBILITY = "accessibility"
        }
        const val CHAT = "chat"

        object Chat {
            const val MESSAGES = "messages"
            const val EXIT = "exit"
        }
    }
}