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
import com.p4c.arguewithai.platform.ai.ChatContract
import com.p4c.arguewithai.platform.ai.FirebaseAiClient
import com.p4c.arguewithai.repository.intervention.JustificationMessage
import com.p4c.arguewithai.repository.intervention.ExitMethod
import com.p4c.arguewithai.repository.intervention.FirestoreJustificationRepository
import com.p4c.arguewithai.repository.intervention.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

object LlmChatbotActivityStatus {
    @Volatile var isOpen: Boolean = false
}

private data class LlmChatbotState(
    var isUserTurn: Boolean = false,
    var finalMessageShown: Boolean = false,
    var hasSentResult: Boolean = false,
    var order: Int = 0, // conversation count
    var index: Int = 0, // question level ( 0, 1, 2, 3 )
    var currentUserMessage: ChatContract.Type = ChatContract.Type("", false)
)

class LlmChatbotActivity : ComponentActivity() {
    private lateinit var ui: ChatUiRefs
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var bottomBar: View
    private lateinit var adapter: ChatAdapter

    private val messages = mutableListOf<Message>()
    private val maxIndex: Int = 3
    private val _chatHistory = mutableListOf<Content>()

    private val repo = FirestoreJustificationRepository()

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
    private val aiClient = FirebaseAiClient(JustificationPrompts.SYSTEM_PROMPT)

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        LlmChatbotActivityStatus.isOpen = true
        setupUI()
        sendFirstChatbotMessage()
    }
    // ---------------------------
    // Basic Code
    // ---------------------------
    private fun sendUserMessage(currentMessage: String) {
        if (!state.isUserTurn) return
        Logger.d("[USER]채팅 입력 시작")
        showMessage(Sender.USER, currentMessage, state.order, state.index)
        state.currentUserMessage = state.currentUserMessage.copy(text = currentMessage)
        updateSendButtonState()
        sendChatbotMessage()
    }

    private fun sendFirstChatbotMessage() {
        Logger.d("[First Chatbot Message]챗봇 첫 메세지 시작")
        lifecycleScope.launch {
            try {
                val response = getChatbotMessage { getFirstJustificationResponse() }
                showMessage(Sender.AI, response.text, state.order, state.index)
                state.isUserTurn = true
            } catch (_: Exception) {
                showMessage(Sender.AI, "메시지를 불러오는데 실패했습니다.", state.order, state.index)
            }
        }
    }

    private fun sendChatbotMessage() {
        lifecycleScope.launch {
            try {
                val currentIdx = state.index.coerceIn(0, maxIndex)
                val currentOrder = state.order
                val currentMessage = state.currentUserMessage

                val response = getChatbotMessage { getJustificationResponse(currentIdx, currentMessage.text) }
                state.currentUserMessage = state.currentUserMessage.copy(score = response.score)
                saveScoreInFirebase(response.score, currentOrder)

                if (currentIdx >= maxIndex) { state.finalMessageShown = true }
                if(response.score) { state.index = (state.index + 1).coerceAtMost(maxIndex) }

                state.order++
                showMessage(Sender.AI, response.text, state.order, state.index)

                handleTurnTransition()
            } catch (_: Exception) {
                showMessage(Sender.AI, "메시지를 불러오는데 실패했습니다.", state.order, state.index)
            }
        }
    }

    // ---------------------------
    // Process Chat Flow
    // ---------------------------
    private suspend fun getChatbotMessage(promptFunction: suspend () -> ChatContract.Type): ChatContract.Type {
        val response: ChatContract.Type = simulateTypingDelay {
            promptFunction()
        }
        return response
    }

    private suspend fun <T> simulateTypingDelay(fetchAction: suspend () -> T): T {
        val typing = addTypingBubble()
        val result = fetchAction()
        removeTypingBubble(typing)

        return result
    }

    private suspend fun handleTurnTransition() {
        if (state.finalMessageShown) {
            state.isUserTurn = false
            updateSendButtonState()

            delay(1500)
            closePrompt(reason = "final_ack")
        } else {
            state.isUserTurn = true
            updateSendButtonState()
        }
    }

    // ---------------------------
    // AI
    // ---------------------------
    private suspend fun getFirstJustificationResponse(): ChatContract.Type {
        val chatPrompt: String = JustificationPrompts.startPrompt()
        return getChatbotResponse(chatPrompt)
    }

    private suspend fun getJustificationResponse(currentIdx: Int, prevMessage: String): ChatContract.Type {
        val chatPrompt: String = JustificationPrompts.getPromptByIndex(currentIdx, prevMessage)
        return getChatbotResponse(chatPrompt)
    }

     private suspend fun getChatbotResponse(prompt: String): ChatContract.Type {
        val response = aiClient.generateResponse(prompt, _chatHistory)
         val raw = response.text ?: return ChatContract.Type("에러가 발생했습니다.", false)
         return try {
             val json = JSONObject(raw)
             ChatContract.Type(
                 text = json.getString(ChatContract.FIELD_TEXT),
                 score = json.getBoolean(ChatContract.FIELD_SCORE)
             )
         } catch (_: Exception) {
             ChatContract.Type("데이터 해석 중 오류가 발생했습니다.", false)
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
            }
        }
    }

    private fun showMessage(sender: Sender, text: String, order: Int, questionIdx: Int) {
        val prev = messages.lastIndex
        if (prev >= 0) adapter.notifyItemChanged(prev)

        val isUser = (sender == Sender.USER)
        messages.add(Message(text = text, isUser = isUser))

        addMessageInList(sender, text)
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.post { recycler.scrollToPosition(messages.lastIndex) }

        saveChatInFirebase(sender, text, order, questionIdx)
    }
    private fun addMessageInList(sender: Sender, text: String) {
        val role = if (sender == Sender.USER) "user" else "model"
        _chatHistory.add(content(role = role) { text(text) })
    }

    private fun saveChatInFirebase(sender: Sender, text: String, order: Int, questionIdx: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                repo.updateMessage(
                    JustificationMessage(sessionId = sessionId, sender = sender, text = text),
                    order,
                    questionIdx
                )
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun saveScoreInFirebase(score: Boolean, index: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                repo.updateScore(sessionId, index, score)
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

        return InputDecision.Accept
    }

    private fun updateSendButtonState() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        btnSend.isEnabled = when {
            !state.isUserTurn -> false
            state.finalMessageShown -> text.isNotEmpty()
            else -> true
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