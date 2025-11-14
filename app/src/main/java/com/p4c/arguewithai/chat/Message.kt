package com.p4c.arguewithai.chat

data class Message(
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)