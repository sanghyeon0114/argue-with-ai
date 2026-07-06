package com.p4c.arguewithai.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.p4c.arguewithai.app.InterventionPrefs
import com.p4c.arguewithai.intervention.ShortFormWatcherManager
import com.p4c.arguewithai.platform.overlay.DebugScreenOverlay
import com.p4c.arguewithai.repository.FirestoreSessionRepository
import com.p4c.arguewithai.repository.SessionId
import com.p4c.arguewithai.repository.SessionRepository
import com.p4c.arguewithai.utils.Logger
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class MyAccessibilityService (
    private val time: TimeProvider = SystemTimeProvider()
) : AccessibilityService() {
    private var interventionEnabled: Boolean = true
    private lateinit var prefs: SharedPreferences
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "intervention_enabled" -> {
                    interventionEnabled = InterventionPrefs.isEnabled(this)
                    Logger.d("🟢 Intervention enabled = $interventionEnabled")
                }
                "debug_overlay_enabled" -> {
                    val enabled = sp.getBoolean("debug_overlay_enabled", false)
                    if (enabled) debugOverlay.start() else debugOverlay.stop()
                    Logger.d("🟣 Debug overlay enabled = $enabled")
                }
            }
        }
    private val repo: SessionRepository = FirestoreSessionRepository()
    private var sessionId: SessionId? = null
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val sessionMutex = Mutex()
    private val watcherManager by lazy {
        ShortFormWatcherManager(
            context = this,
            repo = repo,
            serviceScope = serviceScope,
            sessionMutex = sessionMutex
        )
    }
    private val debugOverlay by lazy { DebugScreenOverlay(applicationContext) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("[AccessibilityService] 연결됨")

        prefs = getSharedPreferences("argue_prefs", MODE_PRIVATE).also {
            interventionEnabled = it.getBoolean("intervention_enabled", true)
            it.registerOnSharedPreferenceChangeListener(prefListener)
        }
        if (prefs.getBoolean("debug_overlay_enabled", false)) {
            debugOverlay.start()
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
        //Logger.d("[EVT] type=${AccessibilityEvent.eventTypeToString(event.eventType)} t=${time.nowMs()}")

        val root = rootInActiveWindow ?: return
//        when (event.eventType) {
////            AccessibilityEvent.TYPE_VIEW_CLICKED ->
////                logEventNode("CLICKED", event)
////
////            AccessibilityEvent.TYPE_VIEW_SELECTED ->
////                logEventNode("SELECTED", event)
////            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
////                logEventNode("CONTENT_CHANGED", event)
////            }
////            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
////                logEventNode("STATE_CHANGED", event)
////            }
//        }



        watcherManager.shortFormTimeCounter.onEvent(event, root, windowList = windows,time.nowMs(), onScreenChanged = { label -> debugOverlay.update(label) })
        //watcherManager.sessionWatcher.onEvent(event, root, time.nowMs())
    }
    private fun logEventNode(tag: String, event: AccessibilityEvent) {
        val node = event.source
        val pkg = event.packageName

        if (node == null) {
            Logger.d("[$tag] source=null pkg=$pkg")
            return
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val info = buildList {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { add("text=\"$it\"") }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add("desc=\"$it\"") }
            node.viewIdResourceName?.let { add("id=${it.substringAfterLast('/')}") }
            add("selected=${node.isSelected}")
            add("clickable=${node.isClickable}")
        }.joinToString(" ")

        Logger.d("[$tag] pkg=$pkg  $cls  $info  ${rect.toShortString()}")

        val knownTabs = listOf("feed_tab", "clips_tab", "search_tab", "direct_tab", "profile_tab")
        for (suffix in knownTabs) {
            val matches: MutableList<AccessibilityNodeInfo>? =
                node.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$suffix")
            val found: AccessibilityNodeInfo? = matches?.firstOrNull { it.isVisibleToUser }
            if (found != null) {
                val sel = found.isSelected
                Logger.d("    └ contains $suffix (selected=$sel)")
            }
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
}