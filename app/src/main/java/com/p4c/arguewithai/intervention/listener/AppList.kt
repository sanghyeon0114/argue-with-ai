package com.p4c.arguewithai.intervention.listener

enum class SocialMediaApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    YOUTUBE("com.google.android.youtube", "youtube"),
    SYSTEM("com.android.systemui", "system"),
    KEYBOARD("com.samsung.android.honeyboard", "keyboard"),
    INTERVENTION("com.p4c.arguewithai", "arguewithai"),
    NONE("NONE", "none");

    companion object {
        fun find(pkg: String): SocialMediaApp {
            return entries.find { it != NONE && it.pkg == pkg } ?: NONE
        }
    }
}