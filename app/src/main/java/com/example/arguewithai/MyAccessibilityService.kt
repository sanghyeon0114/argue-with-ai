package com.example.arguewithai


import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.arguewithai.chat.ChatActivity
import com.example.arguewithai.firebase.FirestoreSessionRepository
import com.example.arguewithai.firebase.SessionId
import com.example.arguewithai.firebase.SessionRepository
import com.example.arguewithai.utils.Logger
import com.example.arguewithai.utils.SystemTimeProvider
import com.example.arguewithai.utils.TimeProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {

    private val repo: SessionRepository = FirestoreSessionRepository()

    private var isShorts: Boolean = false
    private val sessionStack: MutableList<SessionId> = mutableListOf()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sessionMutex = Mutex()
    private var lastChatAt: Long = 0L
    private val cooltime: Long = 5 * 1000L //10 * 60 * 1000L
    private var isPrompt: Boolean = false

    private val promptResultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            val reason = resultData?.getString("reason") ?: "unknown"
            Logger.d("ChatActivity closed. reason=$reason, resultCode=$resultCode")
            reloadCooltime()
        }
    }
    private fun reloadCooltime() {
        val now = time.nowMs()
        lastChatAt = now + cooltime
        isPrompt = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("[AccessibilityService] 연결됨")

        FirebaseApp.initializeApp(this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener { Logger.d("Firebase login ok") }
                .addOnFailureListener { Logger.e("Firebase login fail", it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        val now = time.nowMs()

        if (isShorts && !isPrompt && lastChatAt < now) {
            isPrompt = true
            serviceScope.launch(Dispatchers.Main) {
                showPrompt()
            }
        }

        if(pkg == "com.google.android.youtube") {
            val isYoutubeShorts: Boolean = isYoutubeShortsScreen(root)

            if (isYoutubeShorts && !isShorts) {
                isShorts = true
                serviceScope.launch {
                    runCatching { repo.startSession(app = "YouTube") }
                        .onSuccess { sid ->
                            sessionMutex.withLock { sessionStack.add(sid) }
                            Logger.d("✅ start watching Shorts: ${sid.value}")
                        }
                        .onFailure {
                            isShorts = false
                            Logger.e("❌ failed to start", it)
                        }
                }
            } else if (!isYoutubeShorts && isShorts) {
                isShorts = false
                serviceScope.launch {
                    val sid = sessionMutex.withLock { sessionStack.removeLastOrNull() }
                    if (sid != null) {
                        runCatching { repo.endSession(sid) }
                            .onSuccess { Logger.d("✅ Shorts 시청 종료: ${sid.value} (stack=${stackSize()})") }
                            .onFailure { Logger.e("❌ 종료 실패", it) }
                    } else {
                        Logger.w("⚠️ 종료 시점에 sessionId 없음(이전 시작 실패/중복 이벤트 가능)")
                    }
                }
            }
        } else if(pkg == "com.instagram.android") {
            val isInstagramShorts: Boolean = isInstagramReelsScreen(root)

            if (isInstagramShorts && !isShorts) {
                isShorts = true
                serviceScope.launch {
                    runCatching { repo.startSession(app = "Instagram") }
                        .onSuccess { sid ->
                            sessionMutex.withLock { sessionStack.add(sid) }
                            Logger.d("✅ start watching Reels: ${sid.value}")
                        }
                        .onFailure {
                            isShorts = false
                            Logger.e("❌ failed to start", it)
                        }
                }
            } else if (!isInstagramShorts && isShorts) {
                isShorts = false
                serviceScope.launch {
                    val sid = sessionMutex.withLock { sessionStack.removeLastOrNull() }
                    if (sid != null) {
                        runCatching { repo.endSession(sid) }
                            .onSuccess { Logger.d("✅ Reels 시청 종료: ${sid.value} (stack=${stackSize()})") }
                            .onFailure { Logger.e("❌ 종료 실패", it) }
                    } else {
                        Logger.w("⚠️ 종료 시점에 sessionId 없음(이전 시작 실패/중복 이벤트 가능)")
                    }
                }
            }
        } else if(pkg == "com.ss.android.ugc.trill") {
            val isTikTok: Boolean = isTikTokScreen(root)

            if (isTikTok && !isShorts) {
                isShorts = true
                serviceScope.launch {
                    runCatching { repo.startSession(app = "TikTok") }
                        .onSuccess { sid ->
                            sessionMutex.withLock { sessionStack.add(sid) }
                            Logger.d("✅ start watching tiktok: ${sid.value}")
                        }
                        .onFailure {
                            isShorts = false
                            Logger.e("❌ failed to start", it)
                        }
                }
            } else if(!isTikTok && isShorts) {
                isShorts = false
                serviceScope.launch {
                    val sid = sessionMutex.withLock { sessionStack.removeLastOrNull() }
                    if (sid != null) {
                        runCatching { repo.endSession(sid) }
                            .onSuccess { Logger.d("✅ tiktok 시청 종료: ${sid.value} (stack=${stackSize()})") }
                            .onFailure { Logger.e("❌ 종료 실패", it) }
                    } else {
                        Logger.w("⚠️ 종료 시점에 sessionId 없음(이전 시작 실패/중복 이벤트 가능)")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // pass
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { closeAllSessions(reason = "session destroy") }
        serviceScope.cancel()
    }

    private suspend fun closeAllSessions(reason: String) {
        val toClose: List<SessionId> = sessionMutex.withLock {
            val copy = sessionStack.toList()
            sessionStack.clear()
            copy
        }
        if (toClose.isEmpty()) return

        Logger.d("ℹ️ 열린 세션 ${toClose.size}건 종료 처리 시작 ($reason)")
        toClose.asReversed().forEach { sid -> // 최신부터 종료
            runCatching { repo.endSession(sid) }
                .onSuccess { Logger.d("✅ 종료 완료: ${sid.value}") }
                .onFailure { Logger.e("❌ 종료 실패: ${sid.value}", it) }
        }
    }

    private suspend fun stackSize(): Int =
        sessionMutex.withLock { sessionStack.size }

    private fun isYoutubeShortsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.view.View" &&
                node.viewIdResourceName?.endsWith("reel_progress_bar") == true) found++

            if (node.className == "android.widget.FrameLayout" &&
                node.viewIdResourceName?.endsWith("reel_player_page_container") == true) found++

            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName?.endsWith("reel_time_bar") == true) found++
        }

        return found >= 2
    }

    private fun isInstagramReelsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_author_info_component") {
                found++
            }

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_author_username") {
                found++
            }

            if (node.className == "android.view.ViewGroup" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_caption_component") {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/like_button") {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/direct_share_button") {
                found++
            }

            if (node.className == "android.widget.ImageView" &&
                node.viewIdResourceName == "com.instagram.android:id/clips_ufi_more_button_component") {
                found++
            }
        }
        return found >= 5
    }

    private fun isTikTokScreen(root: AccessibilityNodeInfo): Boolean {
        var found = 0

        root.walkNodes { node ->
            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/ew0") found++

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/dnl") found++

            if (node.className == "android.widget.Button" &&
                node.viewIdResourceName == "com.ss.android.ugc.trill:id/ggg") found++
        }

        return found >= 3
    }

    private inline fun AccessibilityNodeInfo.walkNodes(visit: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            visit(node)

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
    }
    private fun showPrompt() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("receiver", promptResultReceiver)
        }
        startActivity(intent)
    }
}