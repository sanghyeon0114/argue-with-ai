package com.p4c.arguewithai


import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.chat.ChatActivity
import com.p4c.arguewithai.firebase.FirestoreSessionRepository
import com.p4c.arguewithai.firebase.SessionId
import com.p4c.arguewithai.firebase.SessionRepository
import com.p4c.arguewithai.utils.Logger
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {
    private var interventionEnabled: Boolean = true
    private lateinit var prefs: SharedPreferences
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "intervention_enabled") {
                interventionEnabled = InterventionPrefs.isEnabled(this)
                Logger.d("🟢 Intervention enabled = $interventionEnabled")
            }
        }
    private val repo: SessionRepository = FirestoreSessionRepository()
    private var sessionId: SessionId? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sessionMutex = Mutex()
    private var lastChatAt: Long = 0L
    private val cooltime: Long = 5 * 1000L //10 * 60 * 1000L
    private var isPrompt: Boolean = false
    private var lastEventTime = 0L
    private val eventInterval = 100L
    private val myPkg by lazy { packageName }
    private var stateSince = 0L
    private var lastDetectedApp: String? = null
    private val stableMs = 100L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("[AccessibilityService] 연결됨")

        prefs = getSharedPreferences("argue_prefs", Context.MODE_PRIVATE).also {
            interventionEnabled = it.getBoolean("intervention_enabled", true)
            it.registerOnSharedPreferenceChangeListener(prefListener)
        }

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
        if (pkg == myPkg) {
            if(isShortFormState()) {
                stopShortForm()
            }
            return
        }

        val root = rootInActiveWindow ?: return

        delayCallback(pkg, root)
    }

    private fun delayCallback(pkg: String, root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastEventTime < eventInterval) return
        lastEventTime = now

        if(interventionEnabled) interventionOnShortForm(now)
        getShortFormTIme(pkg, root)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun getShortFormTIme(pkg: String, root: AccessibilityNodeInfo) {
        if(!isInternetAvailable()) {
            if(isShortFormState()) {
                stopShortForm()
            }
            return
        }

        val detectedApp: String? = when (pkg) {
            "com.google.android.youtube" -> if (isYoutubeShortsScreen(root)) "YouTube" else null
            "com.instagram.android"      -> if (isInstagramReelsScreen(root)) "Instagram" else null
            "com.ss.android.ugc.trill"   -> if (isTikTokScreen(root)) "TikTok" else null
            else -> null
        }

        val now = System.currentTimeMillis()
        if (detectedApp != lastDetectedApp) {
            lastDetectedApp = detectedApp
            stateSince = now
            return
        }
        if (now - stateSince < stableMs) return

        if(detectedApp != null && !isShortFormState()) {
            startShortForm(detectedApp)
        } else if(detectedApp == null && isShortFormState()) {
            stopShortForm()
        }
    }

    private fun interventionOnShortForm(now: Long) {
        if (isShortFormState() && !isPrompt && lastChatAt < now) {
            startChat()
        }
    }


    override fun onInterrupt() {
        // pass
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        serviceScope.launch { sessionId = null }
        serviceScope.cancel()
    }

    private fun startShortForm(appName: String) {
        serviceScope.launch {
            if(sessionId == null) {
                runCatching { repo.startSession(app = appName) }
                    .onSuccess { sid ->
                        sessionMutex.withLock { sessionId = sid }
                        Logger.d("✅ start watching ${appName} Short-Form: ${sid.value}")
                    }
                    .onFailure {
                        Logger.e("❌ failed to start", it)
                    }
            }
        }
    }

    private fun isShortFormState(): Boolean {
        return sessionId != null
    }

    private fun stopShortForm(appName: String = "") {
        serviceScope.launch {
            val sid = sessionMutex.withLock {
                val current = sessionId
                sessionId = null
                current
            }
            if (sid != null) {
                runCatching { repo.endSession(sid) }
                    .onSuccess { Logger.d("✅${appName} Short-form 시청 종료: ${sid.value}") }
                    .onFailure { Logger.e("❌ 종료 실패", it) }
            } else {
                Logger.d("⚠️ 종료 시점에 sessionId 없음")
            }
        }
    }

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

    private fun startChat() {
        isPrompt = true
        serviceScope.launch(Dispatchers.Main) {
            showPrompt()
        }
    }
}