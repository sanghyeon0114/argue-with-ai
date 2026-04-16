package com.p4c.arguewithai.chat.activity

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.p4c.arguewithai.R
import com.p4c.arguewithai.chat.ChatAdapter
import com.p4c.arguewithai.chat.Message
import com.p4c.arguewithai.chat.prompts.JustificationPrompts
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

object LlmChatbotActivityStatus {
    @Volatile var isOpen: Boolean = false
}

private data class LlmChatbotState(
    var isUserTurn: Boolean = false,
    var finalMessageShown: Boolean = false,
    var hasSentResult: Boolean = false,
    var index: Int = 0,
    var prevMessage: String? = null
)

class LlmChatbotActivity : ComponentActivity() {
    private lateinit var ui: ChatUiRefs
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var bottomBar: View
    private lateinit var adapter: ChatAdapter

    private val messages = mutableListOf<Message>()
    private val messageList = mutableListOf<Content>()

    private val repo = FirestoreChatRepository()

    private var state = LlmChatbotState()
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
        "지금 이 영상을 보게 된 이유가 떠오르시나요?",
        "지금 보내고 있는 이 시간이 의미 있다고 느껴지시나요?",
        "이 선택이 나중에도 괜찮다고 느껴질까요?",
    )
    private val maxIndex: Int = localQuestionsCache.lastIndex

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        LlmChatbotActivityStatus.isOpen = true
        preloadLocalQuestions()
        setupUI()
        sendChatbotMessage("")
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
    private fun sendChatbotMessage(prevUserMessage: String) {
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
        state.index = (state.index + 1).coerceAtMost(maxIndex)

        sendChatbotMessage(currentMessage)
    }

    // ---------------------------
    // AI
    // ---------------------------
    private suspend fun getAIText(currentIdx: Int, prevMessage: String): String {
        val chatPrompt: String = when(currentIdx) {
            0 -> JustificationPrompts.firstTextPrompt()
            1 -> JustificationPrompts.secondTextPrompt(prevMessage)
            else -> JustificationPrompts.finalTextPrompt()
        }

        return getAITextToLLM(chatPrompt)
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
            state.finalMessageShown -> text.isNotEmpty()
            else -> text.length >= 3
        }
        if (btnSend.isEnabled) {
            btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.activate_send_button)
            )
        } else {
            btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.inactivate_send_button)
            )
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
        if (isFinishing) LlmChatbotActivityStatus.isOpen = false
    }
}