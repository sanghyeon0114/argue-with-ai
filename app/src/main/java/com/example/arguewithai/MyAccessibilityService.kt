package com.example.arguewithai


import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyAccessibilityService : AccessibilityService() {

    private val repo: SessionRepository = FirestoreSessionRepository()

    private var isShorts: Boolean = false
    private var currentSessionId: SessionId? = null
    private val sessionStack: MutableList<SessionId> = mutableListOf()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyService", "[AccessibilityService] 접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return

        // 유튜브 외로 전환되었을 때, 열린 세션이 있으면 종료
        if (pkg != "com.google.android.youtube") {
            if (isShorts) {
                isShorts = false
                currentSessionId?.let { sid ->
                    serviceScope.launch {
                        closeAllSessions(reason = "앱 이탈")
                    }
                }
            }
            return
        }

        val isYoutubeShorts: Boolean = isYoutubeShortsScreen(root)

        // OFF -> ON
        if (isYoutubeShorts && !isShorts) {
            isShorts = true
            serviceScope.launch {
                runCatching { repo.startSession(app = "YouTube") }
                    .onSuccess { sid ->
                        sessionMutex.withLock { sessionStack.add(sid) }
                        Log.d("MyService", "✅ Shorts 시청 시작: ${sid.value}")
                    }
                    .onFailure {
                        isShorts = false
                        Log.e("MyService", "❌ 시작 실패", it)
                    }
            }
        }

        // 상태 전이(ON -> OFF): 종료
        if (!isYoutubeShorts && isShorts) {
            isShorts = false
            serviceScope.launch {
                val sid = sessionMutex.withLock { sessionStack.removeLastOrNull() }
                if (sid != null) {
                    runCatching { repo.endSession(sid) }
                        .onSuccess { Log.d("MyService", "✅ Shorts 시청 종료: ${sid.value} (stack=${stackSize()})") }
                        .onFailure { Log.e("MyService", "❌ 종료 실패", it) }
                } else {
                    Log.w("MyService", "⚠️ 종료 시점에 sessionId 없음(이전 시작 실패/중복 이벤트 가능)")
                }
            }
        }
    }

    override fun onInterrupt() {
        // 필요 시 인터럽트 처리
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { closeAllSessions(reason = "session destroy") }
        serviceScope.cancel()
    }

//    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
//        val indent = " ".repeat(depth * 2)
//        Log.d(
//            "MyService",
//            "$indent- class=${node.className}, text=${node.text}, contentDesc=${node.contentDescription}, viewId=${node.viewIdResourceName}"
//        )
//        for (i in 0 until node.childCount) {
//            node.getChild(i)?.let { dumpNode(it, depth + 1) }
//        }
//    }

    private suspend fun closeAllSessions(reason: String) {
        val toClose: List<SessionId> = sessionMutex.withLock {
            val copy = sessionStack.toList()
            sessionStack.clear()
            copy
        }
        if (toClose.isEmpty()) return

        Log.d("MyService", "ℹ️ 열린 세션 ${toClose.size}건 종료 처리 시작 ($reason)")
        toClose.asReversed().forEach { sid -> // 최신부터 종료
            runCatching { repo.endSession(sid) }
                .onSuccess { Log.d("MyService", "✅ 종료 완료: ${sid.value}") }
                .onFailure { Log.e("MyService", "❌ 종료 실패: ${sid.value}", it) }
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
}