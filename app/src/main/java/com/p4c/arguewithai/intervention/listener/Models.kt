package com.p4c.arguewithai.intervention.listener

interface AppScreen

data class PassiveDetectionResult(
    val app: SocialMediaApp,
    val screen: AppScreen?,
    val screenMs: Long,
    val passiveMs: Long,
    val isPassive: Boolean
)