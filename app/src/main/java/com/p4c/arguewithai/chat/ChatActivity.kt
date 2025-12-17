package com.p4c.arguewithai.chat

import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.text.Editable
import android.text.TextWatcher
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.p4c.arguewithai.R
import com.p4c.arguewithai.ai.FirebaseAiClient
import com.p4c.arguewithai.repository.ChatMessage
import com.p4c.arguewithai.repository.ExitMethod
import com.p4c.arguewithai.repository.FirestoreChatRepository
import com.p4c.arguewithai.repository.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object ChatActivityStatus {
    @Volatile var isOpen: Boolean = false
}

private data class ChatState(
    val isUserTurn: Boolean = false,
    val finalMessageShown: Boolean = false,
    val hasSentResult: Boolean = false,
    val totalScore: Int = 0,
    val step: Int = 0
)

// todo : totalScore 관련 코드 추가 ( JSON 형식 반환 )
// https://firebase.google.com/docs/ai-logic/generate-structured-output?hl=ko&api=dev
// todo : Prompt 더 다듬기

class ChatActivity : ComponentActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var bottomBar: View
    private lateinit var adapter: ChatAdapter

    private val messages = mutableListOf<Message>()
    private val messageList = mutableListOf<Content>()

    private val repo = FirestoreChatRepository()

    private var state = ChatState()
    private val sessionId: String by lazy {
        intent.getStringExtra("session_id") ?: System.currentTimeMillis().toString()
    }
    private val receiver by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("receiver", ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("receiver")
        }
    }
    private val aiClient = FirebaseAiClient()

    private val stepIntents: List<String> = listOf(
        "방금 숏폼 시청 시간이 여가처럼 느껴졌는지 스스로 인식하도록 돕는 질문",
        "숏폼 앱을 실행한 동기나 계기를 돌아보게 하는 질문",
        "현재 보내고 있는 시간이 의미 있는지 가치 판단을 유도하는 질문",
        "숏폼 시청에 대한 후회 감정의 정도를 성찰하게 하는 질문",
        "그 감정을 만들었던 상황, 생각, 맥락을 구체화하도록 돕는 질문",
        "지금 이 순간에도 계속 영상을 보게 되는 이유를 메타적으로 인식하게 하는 질문",
        "종료"
    )
    private var intentCount: Int = 0

    private var localQuestionsCache: MutableList<String> = mutableListOf(
        "조금 전 숏폼 시청 시간이 여유롭고 편안한 여가처럼 느껴졌나요?",
        "이번에 숏폼 앱을 켜신 특별한 이유가 있나요?",
        "지금 보내고 있는 시간이 당신에게 얼마나 의미 있다고 느껴지나요?",
        "방금 숏폼을 시청한 시간이 얼마나 후회된다고 느끼시나요?",
        "그렇게 느끼신 데에는 이유가 있을 것 같아요. 어떤 상황이나 생각 때문에 그런 감정을 느끼셨나요?",
        "지금 영상을 계속 시청하게 되는 이유가 무엇이라고 느끼시나요?",
        "답변 감사합니다. 대화는 여기서 마치겠습니다."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatActivityStatus.isOpen = true
        preloadLocalQuestions()
        setupUI()
        sendAiMessage(makePersonaMessage())
    }

    // ---------------------------
    // load Local Question
    // ---------------------------
    private fun preloadLocalQuestions() {
        for (stepIdx in 0 until stepIntents.size) {
            runCatching {
                val fileName = "q${stepIdx + 1}.json"
                val jsonText = assets.open(fileName)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val obj = JSONObject(jsonText)
                val arr = obj.getJSONArray("messages")
                if (arr.length() > 0) {
                    val picked = arr.getString((0 until arr.length()).random())
                    localQuestionsCache[stepIdx] = picked
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    // ---------------------------
    // AI
    // ---------------------------
    private fun makePersonaMessage(): String {
        val intent = stepIntents[intentCount]
        val systemPersona = """
            당신은 숏폼 시청 중인 사용자가 스스로의 상태를 성찰하도록 돕는 '친근한 코치'입니다.
            현재 사용자는 10분 연속으로 숏폼을 시청한 상황입니다.
            
            역할:
            - 판단하거나 설득하지 않는다
            - 사용자의 선택을 바꾸려 하지 않는다
            - 오직 인식과 성찰만 돕는다
            
            출력 규칙:
            - 반드시 두 문장만 출력한다
            - 첫 문장: 사용자의 현재 상태를 부드럽게 공감하는 문장
            - 두 번째 문장: 열린 질문 하나
            - 설명, 분석, 해석, 평가, 명령 금지
            - 왜냐하면/그래서 같은 인과 설명 금지
            - 한국어로 작성
            
            톤:
            - 차분하고 짧으며 압박감이 없어야 한다
            - 치료사나 훈계자가 아니라, 곁에서 묻는 코치처럼 말한다
            
           현재 단계 목표:
            - $intent
            
            첫 번째 문장은 친절하고 밝게 인사하세요
            두 번째 문장은 지금 단계에 맞는 질문을 하나 생성하세요
    """.trimIndent()
        addMessageInList(Sender.USER, systemPersona)
        return systemPersona
    }

    private fun buildPrompt(userAnswer: String): String {
        val intent = stepIntents[intentCount]

        return """
            현재 단계 목표:
            - $intent
            
            사용자의 직전 답변:
            "$userAnswer"
        
            지금 단계에 맞는 질문을 하나 생성하세요
            """.trimIndent()
    }

    private fun sendAiMessage(prompt: String) {
        val typing = addTypingBubble()

        lifecycleScope.launch {
            try {
                val lastIdx = localQuestionsCache.lastIndex
                val currentIdx = intentCount.coerceIn(0, lastIdx)
                val isFinal = currentIdx >= lastIdx

                val aiReply: String =
                    if (isFinal) {
                        localQuestionsCache[currentIdx]
                    } else {
                        getAiMessage(prompt)
                    }

                removeTypingBubble(typing)
                appendMessage(Sender.AI, aiReply)

                if (isFinal) {
                    state = state.copy(finalMessageShown = true)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                removeTypingBubble(typing)

                val idx = intentCount.coerceIn(0, localQuestionsCache.lastIndex)
                appendMessage(Sender.AI, localQuestionsCache[idx])

                if (idx == localQuestionsCache.lastIndex) {
                    state = state.copy(finalMessageShown = true)
                }

                intentCount = (intentCount + 1).coerceAtMost(localQuestionsCache.lastIndex + 1)

            } finally {
                state = state.copy(isUserTurn = true)
                updateSendButtonState()
            }
        }
    }


    private suspend fun getAiMessage(prompt: String): String {
        val aiReply: String = aiClient.generateText(prompt, messageList).ifBlank { localQuestionsCache[intentCount] }
        intentCount++
        return aiReply
    }

    // ---------------------------
    // Chatting
    // ---------------------------
    private fun sendUserText() {
        val raw = etMessage.text?.toString().orEmpty()
        val text = raw.trim()
        val decision = validateInput(text) ?: return

        when (decision) {
            InputDecision.CloseStopKeyword -> {
                closePrompt(reason = "user_stop_keyword")
                return
            }
            InputDecision.CloseFinalAck -> {
                closePrompt(reason = "final_ack")
                return
            }
            InputDecision.Accept -> {
                etMessage.setText("")
                updateSendButtonState()
                sendUserMessage(text)
            }
        }
    }

    private fun sendUserMessage(text: String) {
        if (!state.isUserTurn) return
        appendMessage(Sender.USER, text)

        state = state.copy(isUserTurn = false)
        val prompt: String = buildPrompt(text)
        sendAiMessage(prompt)
    }

    private fun appendMessage(sender: Sender, text: String) {
        val prev = messages.lastIndex
        if (prev >= 0) adapter.notifyItemChanged(prev)

        val isUser = (sender == Sender.USER)
        messages.add(Message(text = text, isUser = isUser))

        addMessageInList(sender, text)
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.post { recycler.scrollToPosition(messages.lastIndex) }

        val index = (messages.size - 1) / 2
        saveChatInFirebase(sender, text, index)
    }
    private fun addMessageInList(sender: Sender, text: String) {
        val role = if (sender == Sender.USER) "user" else "model"
        messageList.add(content(role = role) { text(text) })
    }

    private fun saveChatInFirebase(sender: Sender, text: String, index: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                repo.appendMessage(
                    ChatMessage(sessionId = sessionId, sender = sender, text = text),
                    index
                )
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun addTypingBubble(): Message {
        val typing = Message(text = "...", isUser = false, isTyping = true)
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

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ---------------------------
    // UI Setup
    // ---------------------------

    private fun setupUI() {
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        Logger.d("ChatActivity started.")

        disableSystemBack()
        hideNavigationBar()

        recycler = requireViewByIdSafe(R.id.recyclerMessages, "recyclerMessages")
        etMessage = requireViewByIdSafe(R.id.etMessage, "etMessage")
        btnSend = requireViewByIdSafe(R.id.btnSend, "btnSend")
        bottomBar = requireViewByIdSafe(R.id.bottomBar, "bottomBar")

        setupRecycler()
        applyInsets((findViewById<ViewGroup>(android.R.id.content)).getChildAt(0))

        btnSend.setOnClickListener { sendUserText() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendUserText(); true } else false
        }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
            }
        })

        etMessage.requestFocus()
        showKeyboard()
        updateSendButtonState()
    }

    private fun setupRecycler() {
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(messages)
        recycler.layoutManager = lm
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.clipToPadding = false
    }

    private fun disableSystemBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Logger.d("🚫 System back is disabled in ChatActivity")
            }
        })
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideNavigationBar() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
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

            val bottomPadding = if (imeVisible) ime.bottom else sys.bottom
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

        bottomBar.doOnLayout { ViewCompat.requestApplyInsets(root) }
    }

    // ---------------------------
    // Input validation & button state
    // ---------------------------

    private sealed interface InputDecision {
        data object Accept : InputDecision
        data object CloseStopKeyword : InputDecision
        data object CloseFinalAck : InputDecision
    }

    private fun validateInput(text: String): InputDecision? {
        if (text.isEmpty()) return null
        if (text == "그만할래") return InputDecision.CloseStopKeyword
        if (state.finalMessageShown) return InputDecision.CloseFinalAck

        if (!state.isUserTurn) {
            Logger.d("❌ Not user's turn")
            return null
        }
         if (text.length < 3) return null

        return InputDecision.Accept
    }

    private fun updateSendButtonState() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        btnSend.isEnabled = when {
            !state.isUserTurn -> false
            text == "그만할래" -> true
            state.finalMessageShown -> text.isNotEmpty()
            else -> text.length >= 3
        }
    }

    private fun exitMethodFor(reason: String): ExitMethod {
        return when (reason) {
            "user_closed" -> ExitMethod.BUTTON
            "user_stop_keyword" -> ExitMethod.BUTTON
            "final_ack" -> ExitMethod.BUTTON
            else -> ExitMethod.NAV_BAR
        }
    }

    private fun closePrompt(reason: String = "user_closed", resultCode: Int = RESULT_OK) {
        val finished = state.finalMessageShown
        val method = exitMethodFor(reason)

        lifecycleScope.launch {
            runCatching {
                repo.logExit(
                    sessionId = sessionId,
                    finished = finished,
                    method = method,
                    note = reason
                )
            }.onFailure { it.printStackTrace() }
        }

        if (!state.hasSentResult) {
            receiver?.send(resultCode, Bundle().apply {
                putString("reason", reason)
                putInt("totalScore", state.totalScore)
            })
            state = state.copy(hasSentResult = true)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations && !state.hasSentResult) {
            closePrompt(reason = "destroyed", resultCode = RESULT_CANCELED)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !state.hasSentResult) {
            closePrompt("backgrounded_or_home")
        }
        if (isFinishing) ChatActivityStatus.isOpen = false
    }

    private inline fun <reified T : View> requireViewByIdSafe(id: Int, name: String): T {
        val v = findViewById<T>(id)
        return v ?: error("activity_chat.xml에 id='$name' 뷰가 없습니다.")
    }
}
