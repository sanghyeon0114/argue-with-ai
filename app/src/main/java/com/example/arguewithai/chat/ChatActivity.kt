package com.example.arguewithai.chat

import android.app.Activity
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arguewithai.R
import com.example.arguewithai.firebase.ChatMessage
import com.example.arguewithai.firebase.FirestoreChatRepository
import com.example.arguewithai.firebase.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChatActivity: ComponentActivity() {
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
        "안녕하세요! 지금 보고 계신 영상은 어떤 이유로 보시나요?",
        "이 영상을 계속 본다면 나중에 후회할 가능성은 얼마나 될까요?",
        "지금 이 시간이 의미 있는 사용이라고 느껴지시나요?",
        "답변 감사합니다. 잠시 생각해보는 시간이 되었길 바랍니다."
    )
    private var aiIndex = 0

    private val receiver by lazy {
        intent.getParcelableExtra<ResultReceiver>("receiver")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        recycler  = requireViewByIdSafe(R.id.recyclerMessages, "recyclerMessages")
        etMessage = requireViewByIdSafe(R.id.etMessage, "etMessage")
        btnSend   = requireViewByIdSafe(R.id.btnSend, "btnSend")
        bottomBar = requireViewByIdSafe(R.id.bottomBar, "bottomBar")

        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(messages)

        recycler.layoutManager = lm
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.clipToPadding = false

        val root = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        applyInsets(root)

        // 대화 시작 시 첫 질문
        addAi(aiMessageList[aiIndex], aiIndex)

        btnSend.setOnClickListener { sendCurrentText() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText(); true
            } else false
        }
    }


    private fun applyInsets(root: View) {
        // 1) 루트: 시스템바만
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, sys.bottom)
            insets
        }

        // 2) RecyclerView: 시스템바 + IME (리스트 안 가리도록)
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sys.bottom + ime.bottom)

            if (imeVisible && messages.isNotEmpty()) {
                recycler.post { recycler.scrollToPosition(messages.lastIndex) }
            }
            insets
        }

        // 3) 하단 바: 시스템바 패딩 + (Fallback) ime 보정
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // 기본: 시스템바만 패딩 -> adjustResize가 높이를 줄이므로 자동으로 키보드 위에 위치
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sys.bottom)

            // Fallback: 일부 기기에서 adjustResize 미동작 시, 즉시 위로 띄우기
            v.translationY = if (imeVisible) -ime.bottom.toFloat() else 0f
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun sendCurrentText() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        addUser(text, aiIndex)
        aiIndex++
        etMessage.text?.clear()

        // AI가 준비된 3개의 질문을 순서대로 던짐
        if (aiIndex < aiMessageList.size) {
            addAi(aiMessageList[aiIndex], aiIndex)
        }
    }

    private fun addUser(text: String, index: Int) {
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

    private fun addAi(text: String, index: Int) {
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

    private inline fun <reified T : View> requireViewByIdSafe(id: Int, name: String): T {
        val v = findViewById<T>(id)
        return v ?: error("activity_chat.xml에 id='$name' 뷰가 없습니다.")
    }

    // 사용자가 닫기 버튼을 누르거나 특정 시점에 종료할 때 호출
    private fun closePrompt(reason: String = "user_closed") {
        receiver?.send(Activity.RESULT_OK, Bundle().apply {
            putString("reason", reason)
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            receiver?.send(Activity.RESULT_CANCELED, Bundle().apply {
                putString("reason", "destroyed")
            })
        }
    }
}
