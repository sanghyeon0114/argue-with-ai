package com.p4c.arguewithai.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend

class FirebaseAiClient(
    private val modelName: String = "gemini-2.5-flash",
    private val backend: GenerativeBackend = GenerativeBackend.googleAI()
) {
    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = backend).generativeModel(modelName)
    }

    suspend fun generateContent(prompt: String): GenerateContentResponse {
        return model.generateContent(prompt)
    }
    suspend fun generateText(prompt: String): String {
        val response = generateContent(prompt)
        return response.text ?: ""
    }
}
