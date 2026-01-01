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
import com.p4c.arguewithai.platform.ai.ChatContract
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
    var index: Int = 0
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
    private val maxIndex: Int = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatActivityStatus.isOpen = true
        preloadLocalQuestions()
        setupUI()
        sendAiMessage(ChatPrompts.firstStagePrompt())
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
    // AI
    // ---------------------------

    private fun sendAiMessage(prompt: String) {
        val typing = addTypingBubble()

        lifecycleScope.launch {
            try {
                val currentIdx = state.index.coerceIn(0, maxIndex)
                val isFinal = currentIdx >= maxIndex

                val messageJson : JSONObject = getAiMessage(prompt)
                val message : String = messageJson.getString("text")
                val score : Int = messageJson.getInt("score")

                val aiReply: String =
                    if (isFinal) {
                        localQuestionsCache[currentIdx]
                    } else {
                        message
                    }

                if(currentIdx != 0) {
                    state.totalScore += score
                    Logger.d("add score : $score / total Score : ${state.totalScore}")
                }



                removeTypingBubble(typing)
                appendMessage(Sender.AI, aiReply)

                if (isFinal) {
                    state.finalMessageShown = true
                }

            } catch (e: Exception) {
                e.printStackTrace()
                removeTypingBubble(typing)

                val idx = state.index.coerceIn(0, maxIndex)
                appendMessage(Sender.AI, localQuestionsCache[idx])

                if (idx == maxIndex) {
                    state.finalMessageShown = true
                }

                state.index = (state.index + 1).coerceAtMost(maxIndex + 1)

            } finally {
                state.isUserTurn = true
                updateSendButtonState()
            }
        }
    }


    private suspend fun getAiMessage(prompt: String): JSONObject {
        val aiReply: ChatContract.Type = aiClient.generateMessageJson(prompt, messageList)

        val message : String = aiReply.text.ifBlank { localQuestionsCache[state.index] }
        val score : Int = aiReply.score

        state.index++

        return JSONObject().apply {
            put("text", message)
            put("score", score)
        }
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

        state.isUserTurn =  true
        val currentIdx = state.index.coerceIn(0, maxIndex)
        val prompt: String = when(currentIdx) {
            1 -> ChatPrompts.secondStagePrompt(text)
            2 -> ChatPrompts.thirdStagePrompt(text)
            3 -> ChatPrompts.forthStagePrompt(text)
            4 -> ChatPrompts.fifthStagePrompt(text)
            5 -> ChatPrompts.sixthStagePrompt(text)
            else -> ChatPrompts.finalStagePrompt(state.totalScore)
        }
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