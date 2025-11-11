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
import com.p4c.arguewithai.firebase.FirestoreChatRepository
import com.p4c.arguewithai.firebase.Sender
import com.p4c.arguewithai.utils.Logger
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
        "답변 감사합니다. 잠시 생각해보는 시간이 되었길 바랍니다.\n뒤로가기를 통해 프롬프트를 종료해주세요."
    )
    private var aiIndex = 0

    private val receiver by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("receiver", ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("receiver")
        }
    }

    private var hasSentResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        Logger.d("ChatActivity started.")

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

        addAi(aiMessageList[aiIndex], aiIndex)

        btnSend.setOnClickListener { sendCurrentText() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText(); true
            } else false
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
                ime.bottom + bottomBar.height
            } else {
                sys.bottom + bottomBar.height
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
        addUser(text, aiIndex)
        aiIndex++
        etMessage.text?.clear()

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

    private fun closePrompt(reason: String = "user_closed", resultCode: Int = RESULT_OK) {
        if (!hasSentResult) {
            receiver?.send(resultCode, Bundle().apply {
                putString("reason", reason)
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

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !hasSentResult) {
            closePrompt("backgrounded_or_home")
        }
    }
}