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
import com.p4c.arguewithai.chat.ui.*
import com.p4c.arguewithai.repository.ChatMessage
import com.p4c.arguewithai.repository.ExitMethod
import com.p4c.arguewithai.repository.FirestoreChatRepository
import com.p4c.arguewithai.repository.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object RuleBasedChatbotActivityStatus {
    @Volatile var isOpen: Boolean = false
}

private data class RuleBasedChatbotState(
    var isUserTurn: Boolean = false,
    var finalMessageShown: Boolean = false,
    var hasSentResult: Boolean = false,
    var index: Int = 0,
    var prevMessage: String? = null,
    var selectedKeyword: String? = null
)

class RuleBasedChatbotActivity : ComponentActivity() {
    private lateinit var ui: ChatUiRefs
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var bottomBar: View
    private lateinit var adapter: ChatAdapter

    private val messages = mutableListOf<Message>()
    private val messageList = mutableListOf<Content>()

    private val repo = FirestoreChatRepository()

    private var state = RuleBasedChatbotState()
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

    private var affirmationPrompts: MutableList<String> = mutableListOf(
        "자제력, 건강, 질서, 끈기, 자기 인식, 자기 관리, 책임감 중에 하나를 선택해주세요.",
        "아래 문장을 직접 완성해주세요.\n" + "저는 ______을(를) 중요하게 생각합니다.",
        "아래 문장을 직접 완성해주세요.\n" + "저는 지금 스마트폰 사용을 멈추고 ______을(를) 하겠습니다.",
        "감사합니당."
    )
    private val maxIndex: Int = affirmationPrompts.lastIndex

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        RuleBasedChatbotActivityStatus.isOpen = true
        setupUI()
        sendChatbotMessage()
    }

    // ---------------------------
    // Process Chat Flow
    // ---------------------------
    private fun sendChatbotMessage() {
        val typing = addTypingBubble()
        val currentIdx = state.index.coerceIn(0, maxIndex)
        val message : String = affirmationPrompts[currentIdx]
        lifecycleScope.launch {
            try {
                delay(500)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                appendMessage(Sender.AI, message)
                removeTypingBubble(typing)
                if (currentIdx >= maxIndex) {
                    state.finalMessageShown = true
                }
                state.isUserTurn = true
                updateSendButtonState(currentIdx)
            }
        }
    }

    private fun sendUserMessage(currentMessage: String) {
        if (!state.isUserTurn) return
        appendMessage(Sender.USER, currentMessage)
        state.isUserTurn =  false
        state.index = (state.index + 1).coerceAtMost(maxIndex)

        if (!state.finalMessageShown) {
            sendChatbotMessage()
        } else {
            val currentIdx = state.index.coerceIn(0, maxIndex)
            updateSendButtonState(currentIdx)
        }
    }

    // ---------------------------
    // Chatting System
    // ---------------------------
    private fun sendUserText() {
        val raw = etMessage.text?.toString().orEmpty()
        val text = raw.trim()
        val currentIdx = state.index.coerceIn(0, maxIndex)
        val decision = validateInput(currentIdx, text) ?: return

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
                updateSendButtonState(currentIdx)
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
        etMessage.addTextChangedListener(ChatTextWatcher {
            val currentIdx = state.index.coerceIn(0, maxIndex)
            updateSendButtonState(currentIdx)
        })

        etMessage.requestFocus()
        showKeyboardFor(etMessage)
        val currentIdx = state.index.coerceIn(0, maxIndex)
        updateSendButtonState(currentIdx)
    }
    // ---------------------------
    // Input validation & button state
    // ---------------------------

    private sealed interface InputDecision {
        data object Accept : InputDecision
        data object CloseStopKeyword : InputDecision
        data object CloseFinalAck : InputDecision
    }
    private fun validateAffirmationMessage(index: Int, text: String): Boolean {
        val cleanText = text.replace("\\s".toRegex(), "")

        when (index) {
            0 -> {
                val allowedKeywords = listOf("자제력", "건강", "질서", "끈기", "자기인식", "자기관리", "책임감")
                val selectedKeyword = cleanText.removeSuffix(".")

                if (!allowedKeywords.contains(selectedKeyword)) {
                    return false
                }
                state.selectedKeyword = selectedKeyword
            }
            1 -> {
                val pattern = "^저는.+[을를]중요하게생각합니다\\.?$".toRegex()
                if (!pattern.matches(cleanText)) {
                    return false
                }

                val savedKeyword = state.selectedKeyword
                if (savedKeyword != null && !cleanText.contains(savedKeyword)) {
                    return false
                }
            }
            2 -> {
                val pattern = "^저는지금스마트폰사용을멈추고.+[을를]하겠습니다\\.?$".toRegex()
                if (!pattern.matches(cleanText)) {
                    return false
                }
            }
        }
        return true
    }
    private fun validateInput(index: Int, text: String): InputDecision? {
        if (text.isEmpty()) return null
        if (state.finalMessageShown) return InputDecision.CloseFinalAck

        if (!state.isUserTurn) {
            Logger.d("❌ Not user's turn")
            return null
        }
        if(!validateAffirmationMessage(index, text)) {
            return null
        }
        return InputDecision.Accept
    }

    private fun updateSendButtonState(index: Int) {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        btnSend.isEnabled = when {
            !state.isUserTurn -> false
            state.finalMessageShown -> text.isNotEmpty()
            else -> validateAffirmationMessage(index, text)
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
        if (isFinishing) RuleBasedChatbotActivityStatus.isOpen = false
    }
}