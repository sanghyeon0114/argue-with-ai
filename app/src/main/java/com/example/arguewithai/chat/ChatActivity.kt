package com.example.arguewithai.chat

import android.os.Bundle
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

class ChatActivity: ComponentActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()

    private val aiMessageList = listOf(
        "안녕하세요! 지금 보고 계신 영상은 어떤 이유로 보시나요?",
        "이 영상을 계속 본다면 나중에 후회할 가능성은 얼마나 될까요?",
        "지금 이 시간이 의미 있는 사용이라고 느껴지시나요?",
        "답변 감사합니다. 잠시 생각해보는 시간이 되었길 바랍니다."
    )
    private var aiIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        recycler  = requireViewByIdSafe(R.id.recyclerMessages, "recyclerMessages")
        etMessage = requireViewByIdSafe(R.id.etMessage, "etMessage")
        btnSend   = requireViewByIdSafe(R.id.btnSend, "btnSend")

        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter(messages)

        recycler.layoutManager = lm
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.clipToPadding = false

        val root = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        applyInsets(root)

        // 대화 시작 시 첫 질문
        addAi(aiMessageList[aiIndex])
        aiIndex++

        btnSend.setOnClickListener { sendCurrentText() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText(); true
            } else false
        }
    }

    private fun applyInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottom = if (imeVisible) ime.bottom else sys.bottom

            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, bottom)

            recycler.setPadding(
                recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, bottom
            )

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun sendCurrentText() {
        val text = etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        addUser(text)
        etMessage.text?.clear()

        // AI가 준비된 3개의 질문을 순서대로 던짐
        if (aiIndex < aiMessageList.size) {
            addAi(aiMessageList[aiIndex])
            aiIndex++
        }
    }

    private fun addUser(text: String) {
        messages.add(Message(text = text, isUser = true))
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.scrollToPosition(messages.lastIndex)
    }

    private fun addAi(text: String) {
        messages.add(Message(text = text, isUser = false))
        adapter.notifyItemInserted(messages.lastIndex)
        recycler.scrollToPosition(messages.lastIndex)
    }

    private inline fun <reified T : View> requireViewByIdSafe(id: Int, name: String): T {
        val v = findViewById<T>(id)
        return v ?: error("activity_chat.xml에 id='$name' 뷰가 없습니다.")
    }
}
