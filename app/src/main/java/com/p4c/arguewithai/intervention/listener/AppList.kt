package com.p4c.arguewithai.intervention.listener

enum class SocialMediaApp(val pkg: String, val label: String) {
    PASSIVE_INSTAGRAM("passive_instagram", "passive_instagram"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    SYSTEM("com.android.systemui", "system"),
    KEYBOARD("com.samsung.android.honeyboard", "keyboard"),
    INTERVENTION("com.p4c.arguewithai", "arguewithai"),
    NONE("NONE", "none");

    companion object {
        fun resolve(pkg: String): SocialMediaApp {
            return entries.find { it != NONE && it.pkg == pkg } ?: NONE
        }
    }
}