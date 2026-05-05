package com.p4c.arguewithai.repository

object FirebaseConfig {
    const val ROOT_COLLECTION = "users"

    object User {
        const val SESSIONS = "sessions"
        const val PROFILES = "profiles"

        object Profiles {
            const val ACCESSIBILITY = "accessibility"
        }

        const val BLOCKING = "blocking"
        const val AFFIRMATION = "affirmation"
        const val JUSTIFICATION = "justification"

        object Blocking {
            const val MESSAGES = "messages"
            const val EXIT = "exit"
        }

        object Affirmation {
            const val MESSAGES = "messages"
            const val EXIT = "exit"
        }

        object Justification {
            const val MESSAGES = "messages"
            const val SCORE = "score"
            const val EXIT = "exit"
        }
    }
}