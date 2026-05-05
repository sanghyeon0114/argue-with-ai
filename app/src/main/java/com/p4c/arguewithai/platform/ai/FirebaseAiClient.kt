package com.p4c.arguewithai.platform.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig

class FirebaseAiClient(
    private val systemInstruction: String,
    private val modelName: String = "gemini-2.5-flash",
    private val backend: GenerativeBackend = GenerativeBackend.vertexAI(),
) {
    private val combinedConfig = generationConfig {
        thinkingConfig = thinkingConfig { thinkingBudget = 0 }
        maxOutputTokens = 300
        responseMimeType = "application/json"
        responseSchema = ChatContract.schema
    }

    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = backend).generativeModel(
            modelName = modelName,
            generationConfig = combinedConfig,
            systemInstruction = content { text(systemInstruction) }
        )
    }

    suspend fun generateResponse(prompt: String, history: List<Content>): GenerateContentResponse {
        val chat = model.startChat(history = history)
        return chat.sendMessage(prompt)
    }
}
