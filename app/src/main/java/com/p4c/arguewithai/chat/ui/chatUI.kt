package com.p4c.arguewithai.chat.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.p4c.arguewithai.R
import com.p4c.arguewithai.chat.ChatActivity

internal data class ChatUiRefs(
    val recycler: RecyclerView,
    val etMessage: EditText,
    val btnSend: ImageButton,
    val bottomBar: View,
    val root: View
)

internal inline fun <reified T : View> Activity.requireView(id: Int, name: String): T {
    return findViewById<T>(id) ?: error("activity_chat.xml에 id='$name' 뷰가 없습니다.")
}

internal fun ChatActivity.bindChatUi(): ChatUiRefs {
    val root = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
    return ChatUiRefs(
        recycler = requireView(R.id.recyclerMessages, "recyclerMessages"),
        etMessage = requireView(R.id.etMessage, "etMessage"),
        btnSend = requireView(R.id.btnSend, "btnSend"),
        bottomBar = requireView(R.id.bottomBar, "bottomBar"),
        root = root
    )
}

internal fun RecyclerView.setupChatRecycler(adapter: RecyclerView.Adapter<*>) {
    layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
    this.adapter = adapter
    itemAnimator = null
    clipToPadding = false
}

internal fun ComponentActivity.disableSystemBackForChat() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { /* ignore */ }
    })
}

internal fun Activity.hideNavigationBarForChat() {
    val controller = WindowInsetsControllerCompat((this).window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.navigationBars())

    window.decorView.setOnApplyWindowInsetsListener { _, insets ->
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        insets
    }
}

internal fun ChatUiRefs.applyInsetsForChat(onImeShownScrollToBottom: () -> Unit) {
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

        if (imeVisible) onImeShownScrollToBottom()
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

internal fun Activity.showKeyboardFor(et: EditText) {
    val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
}

internal fun ChatUiRefs.bindSendActions(
    onSend: () -> Unit
) {
    btnSend.setOnClickListener { onSend() }
    etMessage.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_SEND) { onSend(); true } else false
    }
}

internal class ChatTextWatcher(
    private val onChanged: () -> Unit
) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun afterTextChanged(s: Editable?) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
}
