package com.p4c.arguewithai.chat.prompts

import android.content.Context
import com.p4c.arguewithai.platform.ai.chat.ChatAIClient
import com.p4c.arguewithai.platform.ai.chat.ChatContract
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

object FirstQuestionCache {
    private const val PREFS = "first_question_cache"
    private const val KEY_QUEUE = "queue"
    private const val KEY_LAST_USED = "last_used"
    private const val TARGET_SIZE = 2
    private const val LLM_INTERVENTION_TYPE = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refillMutex = Mutex()
    private val lock = Any()

    fun take(context: Context): String? = synchronized(lock) {
        val prefs = prefs(context)
        val queue = readQueue(context)
        if (queue.isEmpty()) return null
        val first = queue.removeAt(0)
        prefs.edit()
            .putString(KEY_QUEUE, JSONArray(queue).toString())
            .putString(KEY_LAST_USED, first)
            .apply()
        Logger.d("[FirstQuestionCache] take (remaining=${queue.size})")
        first
    }

    fun refillAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refill(appContext) }
    }

    private suspend fun refill(context: Context) {
        if (!isLlmCondition(context)) return
        refillMutex.withLock {
            try {
                val client = ChatAIClient(JustificationPrompts.SYSTEM_PROMPT)
                while (readQueue(context).size < TARGET_SIZE) {
                    val text = generate(client, avoid = avoidList(context)) ?: break
                    synchronized(lock) {
                        val queue = readQueue(context)
                        queue.add(text)
                        prefs(context).edit().putString(KEY_QUEUE, JSONArray(queue).toString()).apply()
                    }
                    Logger.d("[FirstQuestionCache] stored (size=${readQueue(context).size})")
                }
            } catch (e: Exception) {
                Logger.e("[FirstQuestionCache] refill failed", e)
            }
        }
    }

    private suspend fun generate(client: ChatAIClient, avoid: List<String>): String? {
        val prompt = buildString {
            append(JustificationPrompts.startPrompt())
            if (avoid.isNotEmpty()) {
                append("\n\n[참고]\n- 아래 문장과 똑같은 문장은 피하세요. 단, 표현을 억지로 바꾸지 말고 자연스러움을 우선하세요.\n")
                avoid.forEach { append("- \"").append(it).append("\"\n") }
            }
        }
        val raw = client.generateResponse(prompt, emptyList()).text ?: return null
        val text = runCatching { JSONObject(raw).getString(ChatContract.FIELD_TEXT).trim() }.getOrNull()
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun avoidList(context: Context): List<String> = synchronized(lock) {
        val lastUsed = prefs(context).getString(KEY_LAST_USED, null)
        readQueue(context) + listOfNotNull(lastUsed)
    }

    private fun readQueue(context: Context): MutableList<String> {
        val raw = prefs(context).getString(KEY_QUEUE, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { arr.getString(it) }
        }.getOrDefault(mutableListOf())
    }

    private fun isLlmCondition(context: Context): Boolean =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getInt("intervention_type", 0) == LLM_INTERVENTION_TYPE

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
