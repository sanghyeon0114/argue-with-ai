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
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.p4c.arguewithai.R
import com.p4c.arguewithai.repository.ChatMessage
import com.p4c.arguewithai.repository.ExitMethod
import com.p4c.arguewithai.repository.FirestoreChatRepository
import com.p4c.arguewithai.repository.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ChatActivityStatus {
    @Volatile
    var isOpen: Boolean = false
}

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
    private lateinit var aiMessageList: List<String>

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
    private val aiDelayMs = 300L
    private var isUserTurn = true
    private val serverUri = "http://10.11.71.49:7001"
    private val httpClient = OkHttpClient()
    private var totalScore: Int = 0

    private val questionEndpoints = mapOf(
        0 to "todo",
        1 to "motivation",
        2 to "meaning"
    )

    private val questionFileCount = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatActivityStatus.isOpen = true
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        Logger.d("ChatActivity started.")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Logger.d("🚫 System back (navigation bar / gesture) is disabled in ChatActivity")
                }
            }
        )

        hideNavigationBar()

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

        aiMessageList = loadRandomQuestionsFromAssets().ifEmpty {
            listOf(
                "조금 전 숏폼 시청 시간이 여유롭고 편안한 여가처럼 느껴졌나요?",
                "이번에 숏폼 앱을 켜신 특별한 이유가 있나요?",
                "지금 보내고 있는 시간이 당신에게 얼마나 의미 있다고 느껴지나요?",
                "방금 숏폼을 시청한 시간이 얼마나 후회된다고 느끼시나요?",
                "그렇게 느끼신 데에는 이유가 있을 것 같아요. 어떤 상황이나 생각 때문에 그런 감정을 느끼셨나요?",
                "지금 영상을 계속 시청하게 되는 이유가 무엇이라고 느끼시나요?"
            )
        }
        Logger.d("Loaded AI messages from assets: $aiMessageList")

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

    private fun loadRandomQuestionsFromAssets(): List<String> {
        val result = mutableListOf<String>()

        for (i in 1..questionFileCount) {
            val fileName = "q$i.json"
            try {
                val jsonStr = assets.open(fileName).bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val messagesArray: JSONArray = json.optJSONArray("messages") ?: continue
                if (messagesArray.length() == 0) continue

                val randomIndex = (0 until messagesArray.length()).random()
                val msg = messagesArray.optString(randomIndex, "").trim()

                if (msg.isNotEmpty()) {
                    result.add(msg)
                } else {
                    Logger.e("Empty message in $fileName at index $randomIndex", null)
                }
            } catch (e: Exception) {
                Logger.e("Failed to load or parse $fileName", e)
            }
        }

        return result
    }

    private fun hideNavigationBar() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        controller.hide(
            WindowInsetsCompat.Type.navigationBars()
        )

        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            controller.hide(
                WindowInsetsCompat.Type.navigationBars()
            )
            insets
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

        if (text == "그만할래") {
            val questionIndex = (aiIndex - 1).coerceAtLeast(0)
            addUserMessage(text, questionIndex)
            etMessage.text?.clear()

            closePrompt("user_stop_keyword")
            return
        }

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

        uiScope.launch {
            val typingMessage = addTypingBubble()

//            val delta: Int = runCatching {
//                scoreAnswerFromServer(questionIndex, text)
//            }.getOrElse { e ->
//                Logger.e("Scoring failed", e)
//                0
//            }
            val delta: Int = 0 // todo

            totalScore += delta
            Logger.d("Question $questionIndex delta=$delta, totalScore=$totalScore")

            runCatching {
                repo.updateScore(
                    sessionId = sessionId,
                    order = questionIndex,
                    deltaScore = delta,
                    totalScore = totalScore
                )
            }.onFailure { e ->
                Logger.e("Failed to save score to Firestore", e)
            }

            if (isFinishChat()) {

                val finalText = getFinalMessage(totalScore)

                removeTypingBubble(typingMessage)
                addAiMessage(finalText, aiMessageList.size)

                finalMessageShown = true
                isUserTurn = true
                btnSend.isEnabled = true
                return@launch
            }

            delay(aiDelayMs)
            removeTypingBubble(typingMessage)
            showNextAiMessage()
        }
    }

    private fun getFinalMessage(score: Int): String {
        return when {
            score > 0 -> {
                "답변 감사해요. 지금까지 이야기해 주신 걸 보면, 지금의 숏폼 시청이 어느 정도는 여가이자 의미 있는 시간으로 느껴지는 것 같아요. " +
                        "이 흐름을 잘 유지하되, 너무 길어지지만 않도록 스스로 한 번 더 조절해 보셔도 좋을 것 같아요."
            }
            else -> {
                "답변 감사해요. 대화를 들어보면, 지금 보고 있는 숏폼이 꼭 만족스럽거나 의미 있는 시간만은 아닐 수도 있겠다는 생각이 들어요. " +
                        "혹시 지금 잠깐 멈추고, 원래 하려고 했던 일이나 하고 싶었던 다른 활동을 한 번 떠올려 보는 건 어떨까요?"
            }
        }
    }

    private suspend fun scoreAnswerFromServer(questionIndex: Int, answer: String): Int = withContext(Dispatchers.IO) {
        val endpoint = questionEndpoints[questionIndex] ?: return@withContext 0
        val url = "$serverUri/$endpoint"

        val mediaType = "text/plain; charset=utf-8".toMediaType()
        val requestBody = answer.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)

            return@use when (endpoint) {
                "todo" -> {
                    val s = json.optInt("score", 0)
                    if (s >= 0) -1 else 1
                }

                "motivation" -> {
                    val bin = json.optString("binary", "")
                    if (bin == "GOAL") 1 else -1
                }

                "meaning" -> {
                    val s = json.optInt("score", 0)
                    if (s > 0) 1 else -1
                }

                else -> 0
            }
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
            receiver?.send(resultCode, Bundle().apply {
                putString("reason", reason)
                putInt("totalScore", totalScore)
            })
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

        if (isFinishing) {
            ChatActivityStatus.isOpen = false
        }
    }
}
