package com.example.arguewithai


import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.arguewithai.firebase.FirestoreSessionRepository
import com.example.arguewithai.firebase.SessionId
import com.example.arguewithai.firebase.SessionRepository
import com.example.arguewithai.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyAccessibilityService : AccessibilityService() {

    private val repo: SessionRepository = FirestoreSessionRepository()

    private var isShorts: Boolean = false
    private val sessionStack: MutableList<SessionId> = mutableListOf()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sessionMutex = Mutex()
    private var instagramDumpJob: Job? = null
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
        if(pkg == "com.google.android.youtube") {
            val isYoutubeShorts: Boolean = isYoutubeShortsScreen(root)

            // OFF -> ON
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
            }

            // ON -> OFF
            if (!isYoutubeShorts && isShorts) {
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
//            if (instagramDumpJob != null) return // 이미 실행 중이면 중복 실행 방지
//
//            instagramDumpJob = serviceScope.launch {
//                while (true) {
//                    val root = rootInActiveWindow
//                    if (root == null) {
//                        Logger.w("⛔ dumpNode 실패: rootWindow null")
//                    } else {
//                        Logger.d("📌 [Instagram] dumpNode 시작")
//                        dumpNode(root, 0)
//                    }
//                    delay(10_000) // 10초 대기
//                }
//            }
        } else {
            if (isShorts) isShorts = false
            serviceScope.launch { closeAllSessions(reason = "app out") }
            return
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

//    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
//        val indent = " ".repeat(depth * 2)
//        Logger.d(
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