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
    private val generationConfig = generationConfig {
        thinkingConfig = thinkingConfig { thinkingBudget = 0 }
        maxOutputTokens = 50
        responseMimeType = "application/json"
        responseSchema = ChatContract.schema
    }
    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = backend).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig
        )
    }

    suspend fun generateContent(prompt: String, history: List<Content>): GenerateContentResponse {
        val chat = model.startChat(history = history)
        return chat.sendMessage(prompt)
    }

    suspend fun generateMessageJson(prompt: String, history: List<Content>): ChatContract.Type {
        return try {
            val response = generateContent(prompt, history)
            val raw = response.text ?: ""

            val cleaned = raw
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleaned)

            val text = json.optString(ChatContract.FIELD_TEXT, cleaned)
            val score = json.optInt(ChatContract.FIELD_SCORE, 0)

            ChatContract.Type(text = text, score = score)
        } catch (e: Exception) {
            ChatContract.Type(text = "", score = 0)
        }
    }
}
