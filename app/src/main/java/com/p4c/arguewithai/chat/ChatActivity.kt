package com.p4c.arguewithai.chat

import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.p4c.arguewithai.R
import com.p4c.arguewithai.chat.ui.*
import com.p4c.arguewithai.platform.ai.FirebaseAiClient
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
    var isUserTurn: Boolean = false,
    var finalMessageShown: Boolean = false,
    var hasSentResult: Boolean = false,
    var totalScore: Int = 0,
    var index: Int = 0,
    var prevMessage: String? = null
)

class ChatActivity : ComponentActivity() {
    private lateinit var ui: ChatUiRefs
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

    private var localQuestionsCache: MutableList<String> = mutableListOf(
        "조금 전 숏폼 시청 시간이 여유롭고 편안한 여가처럼 느껴졌나요?",
        "이번에 숏폼 앱을 켜신 특별한 이유가 있나요?",
        "지금 보내고 있는 시간이 당신에게 얼마나 의미 있다고 느껴지나요?",
        "방금 숏폼을 시청한 시간이 얼마나 후회된다고 느끼시나요?",
        "그렇게 느끼신 데에는 이유가 있을 것 같아요. 어떤 상황이나 생각 때문에 그런 감정을 느끼셨나요?",
        "지금 영상을 계속 시청하게 되는 이유가 무엇이라고 느끼시나요?",
        "답변 감사합니다. 대화는 여기서 마치겠습니다."
    )
    private val maxIndex: Int = localQuestionsCache.lastIndex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatActivityStatus.isOpen = true
        preloadLocalQuestions()
        setupUI()
        sendAIMessage("")
    }

    // ---------------------------
    // load Local Question
    // ---------------------------
    private fun preloadLocalQuestions() {
        for (stepIdx in 0 until maxIndex) {
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
    // Process Chat Flow
    // ---------------------------
    private fun sendAIMessage(prevUserMessage: String) {
        val typing = addTypingBubble()
        val currentIdx = state.index.coerceIn(0, maxIndex)
        var message : String = localQuestionsCache[currentIdx]
        lifecycleScope.launch {
            try {
                message = getAIText(currentIdx, prevUserMessage)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                appendMessage(Sender.AI, message)
                removeTypingBubble(typing)
                if (currentIdx >= maxIndex) {
                    state.finalMessageShown = true
                }
                state.isUserTurn = true
                updateSendButtonState()
            }
        }
    }

    private fun sendUserMessage(currentMessage: String) {
        if (!state.isUserTurn) return
        appendMessage(Sender.USER, currentMessage)
        state.isUserTurn =  false
        val currentIdx = state.index.coerceIn(0, maxIndex)

        lifecycleScope.launch {
            try {
                val currentScore: Int = getAIScore(currentIdx, currentMessage)
                state.totalScore += currentScore
                Logger.d("[$currentIdx] current Score : $currentScore / total : ${state.totalScore}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        state.index = (state.index + 1).coerceAtMost(maxIndex)

        sendAIMessage(currentMessage)
    }

    // ---------------------------
    // AI
    // ---------------------------
    private suspend fun getAIText(currentIdx: Int, prevMessage: String): String {
        val chatPrompt: String = when(currentIdx) {
            0 -> ChatPrompts.firstTextPrompt()
            1 -> ChatPrompts.secondTextPrompt(prevMessage)
            2 -> ChatPrompts.thirdTextPrompt(prevMessage)
            3 -> ChatPrompts.forthTextPrompt(prevMessage)
            4 -> ChatPrompts.fifthTextPrompt(prevMessage)
            5 -> ChatPrompts.sixthTextPrompt(prevMessage)
            else -> ChatPrompts.finalTextPrompt(state.totalScore)
        }

        return getAITextToLLM(chatPrompt)
    }
    private suspend fun getAIScore(currentIdx: Int, currentMessage: String): Int {
        val scorePrompt: String? = when(currentIdx) {
            0 -> ChatPrompts.firstScoringPrompt(currentMessage)
            1 -> ChatPrompts.secondScoringPrompt(currentMessage)
            2 -> ChatPrompts.thirdScoringPrompt(currentMessage)
            3 -> ChatPrompts.forthScoringPrompt(currentMessage)
            4 -> ChatPrompts.fifthScoringPrompt(currentMessage)
            5 -> ChatPrompts.sixthScoringPrompt(currentMessage)
            else -> null
        }
        return getAIScoreToLLM(scorePrompt)
    }
     private suspend fun getAITextToLLM(prompt: String): String {
        val response = aiClient.generateText(prompt, messageList)
         val raw = response.text ?: return localQuestionsCache[state.index]
         return runCatching {
             JSONObject(raw).getString("text")
         }.getOrElse {
             localQuestionsCache[state.index]
         }
    }

    private suspend fun getAIScoreToLLM(prompt: String?): Int {
        if(prompt == null){
            return 0
        }
        val response = aiClient.generateScore(prompt)
        val raw = response.text ?: return 0
        return runCatching {
            JSONObject(raw).getInt("score")
        }.getOrElse {
            0
        }
    }

    // ---------------------------
    // Chatting System
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
                state.prevMessage = text
            }
        }
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

        disableSystemBackForChat()
        hideNavigationBarForChat()

        ui = bindChatUi()
        recycler = ui.recycler
        etMessage = ui.etMessage
        btnSend = ui.btnSend
        bottomBar = ui.bottomBar

        adapter = ChatAdapter(messages)
        ui.recycler.setupChatRecycler(adapter)

        ui.applyInsetsForChat {
            if (messages.isNotEmpty()) recycler.post { recycler.scrollToPosition(messages.lastIndex) }
        }

        ui.bindSendActions { sendUserText() }

        etMessage.addTextChangedListener(ChatTextWatcher { updateSendButtonState() })

        etMessage.requestFocus()
        showKeyboardFor(etMessage)
        updateSendButtonState()
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
            state.hasSentResult = true
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
}