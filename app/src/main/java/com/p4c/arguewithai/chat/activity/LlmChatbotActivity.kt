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
import com.p4c.arguewithai.chat.prompts.FallbackQuestions
import com.p4c.arguewithai.chat.prompts.FirstQuestionCache
import com.p4c.arguewithai.chat.prompts.JustificationPrompts
import com.p4c.arguewithai.chat.ui.*
import com.p4c.arguewithai.platform.ai.chat.ChatAIClient
import com.p4c.arguewithai.platform.ai.chat.ChatContract
import com.p4c.arguewithai.platform.ai.scoring.ScoringAIClient
import com.p4c.arguewithai.platform.ai.scoring.ScoringContract
import com.p4c.arguewithai.repository.intervention.JustificationMessage
import com.p4c.arguewithai.repository.intervention.ExitMethod
import com.p4c.arguewithai.repository.intervention.FirestoreJustificationRepository
import com.p4c.arguewithai.repository.intervention.Sender
import com.p4c.arguewithai.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

object LlmChatbotActivityStatus {
    @Volatile var isOpen: Boolean = false
}

private data class JustificationResult(
    val text: String,
    val score: Boolean,
    /** score 가 판정 AI 의 실제 결과인지 (false 면 판정 실패로 통과 처리한 값) */
    val scored: Boolean = true,
    /** text 를 LLM 이 생성했는지 (false: assets 대체 질문·고정 문장) */
    val isLlm: Boolean = true
)

private data class LlmChatbotState(
    var isUserTurn: Boolean = false,
    var finalMessageShown: Boolean = false,
    var hasSentResult: Boolean = false,
    var order: Int = 0, // conversation count
    var index: Int = 0, // question level ( 0, 1, 2, 3 )
    var currentUserMessage: JustificationResult = JustificationResult("", false)
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
    private val CACHED_FIRST_MESSAGE_DELAY_MS: Long = 1000L
    private val AI_TIMEOUT_MS: Long = 10_000L
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
    private val scoringClient = ScoringAIClient(JustificationPrompts.SCORING_SYSTEM_PROMPT)
    private val chatClient = ChatAIClient(JustificationPrompts.SYSTEM_PROMPT)

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        LlmChatbotActivityStatus.isOpen = true
        setupUI()
        lifecycleScope.launch {
            runCatching { repo.logStart(sessionId) }.onFailure { it.printStackTrace() }
        }
        sendFirstChatbotMessage()
    }
    // ---------------------------
    // Basic Code
    // ---------------------------
    private fun sendUserMessage(currentMessage: String) {
        if (!state.isUserTurn) return
        state.isUserTurn = false
        updateSendButtonState()

        showMessage(Sender.USER, currentMessage, state.order, state.index)
        state.currentUserMessage = state.currentUserMessage.copy(text = currentMessage)
        sendChatbotMessage()
    }

    private fun sendFirstChatbotMessage() {
        lifecycleScope.launch {
            try {
                val cached = FirstQuestionCache.take(this@LlmChatbotActivity)
                val firstText = if (cached != null) {
                    simulateTypingDelay { delay(CACHED_FIRST_MESSAGE_DELAY_MS); cached }
                } else {
                    getChatbotMessage { callAi("first") { getFirstJustificationResponse() } }?.text
                }
                val source = when {
                    cached != null -> "cache"
                    firstText != null -> "live"
                    else -> "fallback"
                }
                Logger.d("first message source=$source")
                FirstQuestionCache.refillAsync(this@LlmChatbotActivity)
                showMessage(Sender.CHATBOT, firstText ?: FallbackQuestions.random(this@LlmChatbotActivity, 0), state.order, state.index, isLlm = firstText != null)
                state.isUserTurn = true
                handleTurnTransition()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e("[LlmChatbot] first message failed → fallback", e)
                showMessage(Sender.CHATBOT, FallbackQuestions.random(this@LlmChatbotActivity, 0), state.order, state.index, isLlm = false)
                state.isUserTurn = true
                updateSendButtonState()
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
                if (response.scored) saveScoreInFirebase(response.score, currentOrder)

                if(response.score) { state.index = (state.index + 1).coerceAtMost(maxIndex) }
                if (state.index >= maxIndex) { state.finalMessageShown = true }

                state.order++
                showMessage(Sender.CHATBOT, response.text, state.order, state.index, isLlm = response.isLlm)

                handleTurnTransition()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e("[LlmChatbot] turn failed → fallback", e)
                state.order++
                showMessage(Sender.CHATBOT, fallbackText(state.index), state.order, state.index, isLlm = false)
                state.isUserTurn = true
                updateSendButtonState()
            }
        }
    }

    private suspend fun sendFinalChatbotMessage() {
        try {
            val currentIdx = state.index.coerceIn(0, maxIndex)

            val response = getChatbotMessage { getJustificationResponse(currentIdx, "") }
            state.currentUserMessage = state.currentUserMessage.copy(score = response.score)
            saveScoreInFirebase(response.score, state.order)

            state.order++
            showMessage(Sender.CHATBOT, response.text, state.order, state.index, isLlm = response.isLlm)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e("[LlmChatbot] final message failed", e)
            showMessage(Sender.CHATBOT, JustificationPrompts.FINAL_MESSAGE, state.order, state.index, isLlm = false)
        }
    }

    // ---------------------------
    // Process Chat Flow
    // ---------------------------
    private suspend fun <T> getChatbotMessage(promptFunction: suspend () -> T): T {
        val response: T = simulateTypingDelay {
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

            delay(500)
            sendFinalChatbotMessage()

            // 자동 종료하지 않고, 사용자가 아무 응답이나 보내면 종료한다 (validateInput → CloseFinalAck).
            state.isUserTurn = true
            updateSendButtonState()
        } else {
            state.isUserTurn = true
            updateSendButtonState()
        }
    }

    // ---------------------------
    // AI
    // ---------------------------
    private suspend fun getFirstJustificationResponse(): ChatContract.Type? {
        val chatPrompt: String = JustificationPrompts.startPrompt()
        return getChatResponse(chatPrompt)
    }

    /**
     * 판정 AI 1개 + 문장 AI 2개(true용 / false용)를 동시에 호출하고, 판정 결과에 맞는 문장을 고른다.
     * 어느 호출이든 실패(에러·시간 초과·응답 해석 실패)하면 assets 의 대체 질문으로 이어간다.
     *  - 판정 실패 : 대화가 멈추지 않도록 통과(true)로 처리 (scored = false 로 표시, Firestore 에 기록하지 않음)
     *  - 문장 실패 : 다음에 보여줄 단계의 대체 질문 (true → 다음 단계, false → 같은 단계)
     */
    private suspend fun getJustificationResponse(currentIdx: Int, prevMessage: String): JustificationResult = supervisorScope {
        if (currentIdx >= maxIndex) {
            return@supervisorScope JustificationResult(JustificationPrompts.FINAL_MESSAGE, true, isLlm = false)
        }

        val scoring = async { callAi("scoring") { getScoringResponse(JustificationPrompts.scoringPrompt(prevMessage)) } }
        val passReply = async { callAi("reply:pass") { getChatResponse(JustificationPrompts.replyPrompt(currentIdx, prevMessage, passed = true)) } }
        val retryReply = async { callAi("reply:retry") { getChatResponse(JustificationPrompts.replyPrompt(currentIdx, prevMessage, passed = false)) } }

        val scoringResult = scoring.await()
        val score = scoringResult?.score ?: true
        Logger.d("[Justification] idx=$currentIdx score=$score scored=${scoringResult != null}")

        val text = if (score) {
            retryReply.cancel()
            passReply.await()?.text
        } else {
            passReply.cancel()
            retryReply.await()?.text
        }

        val nextStage = if (score) currentIdx + 1 else currentIdx
        if (text == null) Logger.w("[Justification] reply failed → fallback (stage=$nextStage)")

        JustificationResult(
            text = text ?: fallbackText(nextStage),
            score = score,
            scored = scoringResult != null,
            isLlm = text != null
        )
    }

    /** stage 0~2 → q1~q3 중 무작위 질문, 모든 단계를 통과한 경우 → 마무리 인사 */
    private fun fallbackText(stage: Int): String =
        if (stage >= maxIndex) FallbackQuestions.CLOSING
        else FallbackQuestions.random(this, stage)

    /** AI 호출을 시간 제한과 함께 실행한다. 에러·시간 초과·해석 실패면 null. */
    private suspend fun <T : Any> callAi(tag: String, block: suspend () -> T?): T? {
        return try {
            val result = withTimeoutOrNull(AI_TIMEOUT_MS) { block() }
            if (result == null) Logger.w("[AI:$tag] no result (timeout or invalid response)")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("[AI:$tag] failed", e)
            null
        }
    }

    /** 문장 AI 응답. 비어 있거나 해석에 실패하면 null. */
    private suspend fun getChatResponse(prompt: String): ChatContract.Type? {
        val response = chatClient.generateResponse(prompt, _chatHistory)
        val raw = response.text ?: return null
        return runCatching {
            JSONObject(raw).getString(ChatContract.FIELD_TEXT).trim()
        }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ChatContract.Type(text = it) }
    }

    /** 판정 AI 응답. 해석에 실패하면 null. */
    private suspend fun getScoringResponse(prompt: String): ScoringContract.Type? {
        val response = scoringClient.generateResponse(prompt, _chatHistory)
        val raw = response.text ?: return null
        return runCatching {
            ScoringContract.Type(score = JSONObject(raw).getBoolean(ScoringContract.FIELD_SCORE))
        }.getOrNull()
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

    /** @param isLlm 챗봇 메시지를 LLM 이 생성했는지. 사용자 메시지는 null. */
    private fun showMessage(sender: Sender, text: String, order: Int, questionIdx: Int, isLlm: Boolean? = null) {
        val prev = messages.lastIndex
        if (prev >= 0) adapter.notifyItemChanged(prev)

        val isUser = (sender == Sender.USER)
        messages.add(Message(text = text, isUser = isUser))

        addMessageInList(sender, text)
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.post { recycler.scrollToPosition(messages.lastIndex) }

        //printChatHistory() // DEBUG CODE
        saveChatInFirebase(sender, text, order, questionIdx, isLlm)
    }
    private fun addMessageInList(sender: Sender, text: String) {
        val role = if (sender == Sender.USER) "user" else "model"
        _chatHistory.add(content(role = role) { text(text) })
    }

    private fun saveChatInFirebase(sender: Sender, text: String, order: Int, questionIdx: Int, isLlm: Boolean?) {
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                repo.updateMessage(
                    JustificationMessage(sessionId = sessionId, sender = sender, text = text, isLlm = isLlm),
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

        // 종료 메시지가 화면에 뜨기 전(응답 생성 중)에는 입력을 받지 않는다.
        if (!state.isUserTurn) {
            Logger.d("❌ Not user's turn")
            return null
        }

        if (state.finalMessageShown) return InputDecision.CloseFinalAck

        return InputDecision.Accept
    }

    private fun updateSendButtonState() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        btnSend.isEnabled = when {
            !state.isUserTurn -> false
            state.finalMessageShown -> text.isNotEmpty()
            else -> text.isNotEmpty()
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
            "final_ack" -> ExitMethod.COMPLETE
            else -> ExitMethod.BACKGROUND
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