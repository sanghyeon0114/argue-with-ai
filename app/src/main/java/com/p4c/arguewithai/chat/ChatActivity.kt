package com.p4c.arguewithai.chat

import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.p4c.arguewithai.R
import com.p4c.arguewithai.firebase.ChatMessage
import com.p4c.arguewithai.firebase.ExitMethod
import com.p4c.arguewithai.firebase.FirestoreChatRepository
import com.p4c.arguewithai.firebase.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ChatActivity : ComponentActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var bottomBar: View
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = FirestoreChatRepository()

    private val sessionId: String by lazy {
        intent.getStringExtra("session_id") ?: System.currentTimeMillis().toString()
    }

    private val aiMessageList = listOf(
        "안녕하세요! 혹시 현재 할 일을 미루고 영상을 시청하고 계시지 않으신가요?",
        "왜 숏폼 앱을 실행하셨나요?",
        "어떠한 목적으로 숏폼 영상을 시청하고 계신가요?",
        "현재 보내고 계신 시간이 얼마나 의미가 있다고 생각하시나요?"
    )
    private var aiIndex = 0
    private val userAnswers = mutableListOf<String>()
    private var finalMessageShown = false
    private val finalScore: Int by lazy {
        intent.getIntExtra("final_score", 0)
    }

    private val receiver by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("receiver", ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("receiver")
        }
    }

    private var hasSentResult = false
    private val aiDelayMs = 800L
    private var isUserTurn = true
    private val serverUri = "http://127.0.0.1"
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        Logger.d("ChatActivity started.")

        recycler = requireViewByIdSafe(R.id.recyclerMessages, "recyclerMessages")
        etMessage = requireViewByIdSafe(R.id.etMessage, "etMessage")
        btnSend = requireViewByIdSafe(R.id.btnSend, "btnSend")
        bottomBar = requireViewByIdSafe(R.id.bottomBar, "bottomBar")

        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(messages)

        recycler.layoutManager = lm
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.clipToPadding = false

        val root = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        applyInsets(root)

        showNextAiMessage()

        btnSend.setOnClickListener { sendCurrentText() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText(); true
            } else false
        }

        etMessage.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

        val btnBack = requireViewByIdSafe<ImageButton>(R.id.btnBack, "btnBack")
        btnBack.setOnClickListener {
            closePrompt("user_closed")
        }
    }

    private fun applyInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, 0)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            val bottomPadding = if (imeVisible) {
                ime.bottom
            } else {
                sys.bottom
            }

            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomPadding)

            if (imeVisible && messages.isNotEmpty()) {
                recycler.post { recycler.scrollToPosition(messages.lastIndex) }
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (imeVisible) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                v.translationY = -ime.bottom.toFloat()
            } else {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sys.bottom)
                v.translationY = 0f
            }
            insets
        }

        bottomBar.doOnLayout {
            ViewCompat.requestApplyInsets(root)
        }
    }

    private fun sendCurrentText() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        if (finalMessageShown) {
            closePrompt("final_ack")
            return
        }

        if (!isUserTurn) {
            Logger.d("❌ Not user's turn")
            return
        }

        val questionIndex = (aiIndex - 1).coerceAtLeast(0)

        addUserMessage(text, questionIndex)
        userAnswers.add(text)
        etMessage.text?.clear()

        isUserTurn = false
        btnSend.isEnabled = false

        if (isFinishChat()) {
            val typingMessage = addTypingBubble()

            uiScope.launch {
                val finalText = runCatching {
                    fetchFinalMessageFromServer()
                }.getOrElse {
                    it.printStackTrace()
                    "답변 감사해요. 지금까지의 생각들을 잠깐 정리해 보는 데 이 대화가 조금이나마 도움이 되었기를 바랍니다."
                }

                removeTypingBubble(typingMessage)
                addAiMessage(finalText, aiMessageList.size)

                finalMessageShown = true
                isUserTurn = true
                btnSend.isEnabled = true
            }
            return
        }

        val typingMessage = addTypingBubble()

        uiScope.launch {
            delay(aiDelayMs)
            removeTypingBubble(typingMessage)
            showNextAiMessage()
        }
    }

    private fun showNextAiMessage() {
        if (aiIndex >= aiMessageList.size) return
        val text = aiMessageList[aiIndex]
        addAiMessage(text, aiIndex)
        aiIndex++
        isUserTurn = true
        btnSend.isEnabled = true
    }

    private fun addUserMessage(text: String, index: Int) {
        val prev = messages.lastIndex
        if (prev >= 0) adapter.notifyItemChanged(prev)

        messages.add(Message(text = text, isUser = true))
        adapter.notifyItemInserted(messages.lastIndex)

        recycler.post { recycler.scrollToPosition(messages.lastIndex) }

        uiScope.launch {
            runCatching {
                repo.appendMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        sender = Sender.USER,
                        text = text
                    ),
                    index
                )
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun addAiMessage(text: String, index: Int) {
        val prev = messages.lastIndex
        if (prev >= 0) adapter.notifyItemChanged(prev)

        messages.add(Message(text = text, isUser = false))
        adapter.notifyItemInserted(messages.lastIndex)

        recycler.post { recycler.scrollToPosition(messages.lastIndex) }

        uiScope.launch {
            runCatching {
                repo.appendMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        sender = Sender.AI,
                        text = text
                    ),
                    index
                )
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun isFinishChat(): Boolean {
        return aiIndex >= aiMessageList.size
    }

    private fun addTypingBubble(): Message {
        val typing = Message(
            text = "...",
            isUser = false,
            isTyping = true
        )
        messages.add(typing)
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.post { recycler.scrollToPosition(messages.lastIndex) }
        return typing
    }

    private fun removeTypingBubble(typing: Message) {
        val idx = messages.indexOf(typing)
        if (idx >= 0) {
            messages.removeAt(idx)
            adapter.notifyItemRemoved(idx)
        }
    }

    private inline fun <reified T : View> requireViewByIdSafe(id: Int, name: String): T {
        val v = findViewById<T>(id)
        return v ?: error("activity_chat.xml에 id='$name' 뷰가 없습니다.")
    }

    private suspend fun fetchFinalMessageFromServer(): String = withContext(Dispatchers.IO) {
        val url = "${serverUri}/final_message"

        val bodyJson = JSONObject().apply {
            put("answers", JSONArray(userAnswers))
            put("score", finalScore)
        }.toString()

        val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val responseBody = resp.body?.string().orEmpty()

            val rawMessage = try {
                JSONObject(responseBody).optString("message", responseBody)
            } catch (e: Exception) {
                responseBody
            }

            rawMessage
                .ifBlank {
                    "답변 감사해요. 지금까지의 생각들을 정리해 보는 데 이 대화가 조금이나마 도움이 되었기를 바랍니다."
                }
                .substringBefore('\n')
        }
    }

    private fun closePrompt(reason: String = "user_closed", resultCode: Int = RESULT_OK) {
        val finished = finalMessageShown || isFinishChat()
        val method = if (reason == "user_closed") ExitMethod.BUTTON else ExitMethod.NAV_BAR

        uiScope.launch {
            runCatching {
                repo.logExit(
                    sessionId = sessionId,
                    finished = finished,
                    method = method,
                    note = reason
                )
            }.onFailure { it.printStackTrace() }
        }

        if (!hasSentResult) {
            receiver?.send(resultCode, Bundle().apply { putString("reason", reason) })
            hasSentResult = true
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations && !hasSentResult) {
            closePrompt(reason = "destroyed", resultCode = RESULT_CANCELED)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !hasSentResult) {
            closePrompt("backgrounded_or_home")
        }
    }
}
