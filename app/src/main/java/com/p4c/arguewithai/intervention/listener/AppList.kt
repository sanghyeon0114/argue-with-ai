package com.p4c.arguewithai.intervention.listener

enum class SocialMediaApp(val pkg: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    NONE("NONE", "none");

    companion object {
        fun fromPackageName(pkg: String): SocialMediaApp? {
            return entries.find { it.pkg == pkg }
        }
    }
}