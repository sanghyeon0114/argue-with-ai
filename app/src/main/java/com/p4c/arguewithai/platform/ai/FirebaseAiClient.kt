package com.p4c.arguewithai.platform.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.p4c.arguewithai.utils.Logger
import org.json.JSONObject

class FirebaseAiClient(
    private val modelName: String = "gemini-2.5-flash",
    private val backend: GenerativeBackend = GenerativeBackend.googleAI()
) {
    private val textPromptConfig = generationConfig {
        thinkingConfig = thinkingConfig { thinkingBudget = 0 }
        maxOutputTokens = 200
        responseMimeType = "application/json"
        responseSchema = ChatContract.textSchema
    }
    private val scoringPromptConfig = generationConfig {
        thinkingConfig = thinkingConfig { thinkingBudget = 0 }
        maxOutputTokens = 200
        responseMimeType = "application/json"
        responseSchema = ChatContract.scoringSchema
    }

    private val textModel: GenerativeModel by lazy {
        Firebase.ai(backend = backend).generativeModel(
            modelName = modelName,
            generationConfig = textPromptConfig
        )
    }
    private val scoringModel: GenerativeModel by lazy {
        Firebase.ai(backend = backend).generativeModel(
            modelName = modelName,
            generationConfig = scoringPromptConfig
        )
    }

    suspend fun generateText(prompt: String, history: List<Content>): GenerateContentResponse {
        val chat = textModel.startChat(history = history)
        return chat.sendMessage(prompt)
    }
    suspend fun generateScore(prompt: String): GenerateContentResponse {
        val chat = scoringModel.startChat()
        return chat.sendMessage(prompt)
    }
}
