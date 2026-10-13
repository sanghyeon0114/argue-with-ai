package com.p4c.arguewithai.platform.ai.scoring

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig

class ScoringAIClient(
    private val systemInstruction: String,
    private val modelName: String = "gemini-3.5-flash-lite",
    private val backend: GenerativeBackend = GenerativeBackend.Companion.googleAI(),
) {
    private val combinedConfig = generationConfig {
        thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.Companion.MINIMAL }
        maxOutputTokens = 600
        responseMimeType = "application/json"
        responseSchema = ScoringContract.schema
    }

    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = backend, useLimitedUseAppCheckTokens = false).generativeModel(
            modelName = modelName,
            generationConfig = combinedConfig,
            systemInstruction = content { text(systemInstruction) }
        )
    }

    suspend fun generateResponse(prompt: String, history: List<Content>): GenerateContentResponse {
        val chat = model.startChat(history = history)
        val response = chat.sendMessage(prompt)
        return response
    }
}