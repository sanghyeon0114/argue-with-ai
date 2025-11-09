package com.example.arguewithai.firebase

object FirebaseConfig {
    const val ROOT_COLLECTION = "users"

    object User {
        const val SESSIONS = "sessions"
        const val CLIENT = "client"

        object Client {
            const val ACCESSIBILITY = "accessibility"
            const val DEVICE = "device"
            const val SETTINGS = "settings"
        }
        const val CHAT = "chat"
    }
}