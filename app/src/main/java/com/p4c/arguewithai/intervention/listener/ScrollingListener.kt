package com.p4c.arguewithai.intervention.listener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.p4c.arguewithai.utils.Logger

enum class ShortFormApp(val pkg: String, val label: String) {
    YOUTUBE("com.google.android.youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    TIKTOK("com.ss.android.ugc.trill", "TikTok"),
    SYSTEM("com.android.systemui", "system")
}

interface ShortFormCallback {
    fun onEnter(app: ShortFormApp, sinceMs: Long) {}
    fun onExit(app: ShortFormApp, enteredAtMs: Long, exitedAtMs: Long) {}
    fun onWatchingTick(app: ShortFormApp, enteredAtMs: Long, nowMs: Long, elapsedMs: Long) {}
}

class ShortFormListener(
    private val callback: ShortFormCallback,
    private val stableMs: Long = 150L,
    private val exitGraceMs: Long = 500L,
    private val tickIntervalMs: Long = 100L
) {

    private var currentApp: ShortFormApp? = null
    private var enteredAt: Long = 0L

    private var pendingApp: ShortFormApp? = null
    private var pendingSince: Long = 0L

    private var lastSeenShortFormAt: Long = 0L
    private var lastTickAt: Long = 0L
    private var lastRoot: AccessibilityNodeInfo? = null
    private var lastDumpAt: Long = 0L

    fun onEvent(
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (event == null || root == null) {
            maybeExitOnInvisibility(nowMs)
            return
        }

        val pkg = event.packageName?.toString()

        if (nowMs - lastDumpAt >= 3000L) {
//            Logger.d("==== NODE DUMP ====")
//            root.walkNodes { node ->
//                val cls = node.className?.toString() ?: return@walkNodes
//                val id  = node.viewIdResourceName ?: return@walkNodes
//                Logger.d("[${event.packageName}] $cls | $id")
//            }
//            Logger.d("==== END NODE DUMP ====")
            lastDumpAt = nowMs
        }

        val detected: ShortFormApp? = if (currentApp == null) {
            detectEnter(pkg, root)
        } else {
            detectApp(pkg, root)
        }

        if (detected != null) {
            lastSeenShortFormAt = nowMs
            handleDetected(detected, nowMs)
            maybeWatchingTick(nowMs)
        } else {
            maybeExitOnInvisibility(nowMs)
        }
    }

    private fun handleDetected(app: ShortFormApp, nowMs: Long) {
        if (currentApp == app) return

        if (pendingApp != app) {
            pendingApp = app
            pendingSince = nowMs
            return
        }

        if (nowMs - pendingSince >= stableMs) {
            currentApp?.let { prev ->
                callback.onExit(prev, enteredAt, nowMs)
            }
            currentApp = app
            enteredAt = nowMs
            lastTickAt = nowMs
            callback.onEnter(app, enteredAt)
        }
    }

    private fun maybeWatchingTick(nowMs: Long) {
        val app = currentApp ?: return
        if (nowMs - lastTickAt >= tickIntervalMs) {
            val elapsed = nowMs - enteredAt
            callback.onWatchingTick(app, enteredAt, nowMs, elapsed)
            lastTickAt = nowMs
        }
    }

    private fun maybeExitOnInvisibility(nowMs: Long) {
        val app = currentApp ?: return
        if (nowMs - lastSeenShortFormAt >= exitGraceMs) {

            lastRoot?.let { root ->
                Logger.d("==== EXIT NODE DUMP: ${app.label} ====")
                root.walkNodes { node ->
                    val cls = node.className?.toString() ?: return@walkNodes
                    val id  = node.viewIdResourceName ?: return@walkNodes
                    Logger.d("[${app.pkg}] $cls | $id")
                }
                Logger.d("==== END EXIT NODE DUMP ====")
            }
            callback.onExit(app, enteredAt, nowMs)
            currentApp = null
            pendingApp = null
            lastTickAt = 0L
        }
    }

    private fun detectEnter(pkg: String?, root: AccessibilityNodeInfo): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.YOUTUBE.pkg -> if (isYoutubeISScreen(root)) ShortFormApp.YOUTUBE else null
            ShortFormApp.INSTAGRAM.pkg -> if (isInstagramISScreen(root)) ShortFormApp.INSTAGRAM else null
            ShortFormApp.TIKTOK.pkg -> if (isTikTokISScreen(root)) ShortFormApp.TIKTOK else null
            else -> null
        }
    }

    private fun detectApp(pkg: String?, root: AccessibilityNodeInfo): ShortFormApp? {
        return when (pkg) {
            ShortFormApp.YOUTUBE.pkg -> if (isYoutubeISScreen(root)) ShortFormApp.YOUTUBE else null
            ShortFormApp.INSTAGRAM.pkg -> if (isInstagramISScreen(root)) ShortFormApp.INSTAGRAM else null
            ShortFormApp.TIKTOK.pkg -> if (isTikTokISScreen(root)) ShortFormApp.TIKTOK else null
            ShortFormApp.SYSTEM.pkg -> ShortFormApp.SYSTEM
            else -> null
        }
    }
    companion object {
        fun isInstagramISScreen(root: AccessibilityNodeInfo): Boolean {
            return (isInstagramHomeScreen(root) || isInstagramReelsScreen(root) || isInstagramSearchScreen(root))
        }
        fun isYoutubeISScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                if (node.className == "android.view.View" &&
                    node.viewIdResourceName?.endsWith("reel_progress_bar") == true
                ) found++

                if (node.className == "android.widget.FrameLayout" &&
                    node.viewIdResourceName?.endsWith("reel_player_page_container") == true
                ) found++

                if (node.className == "android.view.ViewGroup" &&
                    node.viewIdResourceName?.endsWith("reel_time_bar") == true
                ) found++
            }

            return found >= 2
        }
        // 1. 홈 피드 화면 감지
        fun isInstagramHomeScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                // 피드 게시물 헤더
                if (node.className == "android.view.ViewGroup" &&
                    node.viewIdResourceName?.endsWith("row_feed_profile_header") == true
                ) found++

                // 피드 좋아요/댓글 버튼 영역
                if (node.className == "android.view.ViewGroup" &&
                    node.viewIdResourceName?.endsWith("row_feed_view_group_buttons") == true
                ) found++

                // 인스타그램 로고
                if (node.className == "android.widget.ImageView" &&
                    node.viewIdResourceName?.endsWith("title_logo") == true
                ) found++
            }

            return found >= 3
        }

        // 2. 릴스 화면 감지
        fun isInstagramReelsScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                // 릴스 전용 뷰페이저
                if (node.className == "androidx.viewpager.widget.ViewPager" &&
                    node.viewIdResourceName?.endsWith("clips_viewer_view_pager") == true
                ) found++

                // 릴스 작성자 유저네임
                if (node.className == "android.widget.Button" &&
                    node.viewIdResourceName?.endsWith("clips_author_username") == true
                ) found++

                // 릴스 UFI (좋아요/댓글 등) 영역
                if (node.className == "android.view.ViewGroup" &&
                    node.viewIdResourceName?.endsWith("clips_ufi_component") == true
                ) found++
            }

            // 단, 홈 피드와 구분: row_feed_view_group_buttons 없어야 함
            var hasFeedButtons = false
            root.walkNodes { node ->
                if (node.viewIdResourceName?.endsWith("row_feed_view_group_buttons") == true)
                    hasFeedButtons = true
            }

            return found >= 3 && !hasFeedButtons
        }

        // 3. DM 리스트 화면 감지
        fun isInstagramDMListScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                // DM 대화 목록 RecyclerView
                if (node.className == "androidx.recyclerview.widget.RecyclerView" &&
                    node.viewIdResourceName?.endsWith("inbox_refreshable_thread_list_recyclerview") == true
                ) found++

                // DM 상단 액션바 타이틀
                if (node.className == "android.widget.Button" &&
                    node.viewIdResourceName?.endsWith("igds_action_bar_title") == true
                ) found++

                // 스냅/스토리 미리보기 영역
                if (node.className == "android.widget.FrameLayout" &&
                    node.viewIdResourceName?.endsWith("direct_quick_snap_consumption_preview") == true
                ) found++
            }

            // 검색창 없어야 DM 리스트 (검색 화면과 구분)
            var hasSearchEdit = false
            root.walkNodes { node ->
                if (node.viewIdResourceName?.endsWith("action_bar_search_edit_text") == true)
                    hasSearchEdit = true
            }

            return found >= 3 && !hasSearchEdit
        }

        // 4. 검색(탐색) 화면 감지
        fun isInstagramSearchScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                // 검색 입력창
                if (node.className == "android.widget.EditText" &&
                    node.viewIdResourceName?.endsWith("action_bar_search_edit_text") == true
                ) found++

                // 탐색 그리드 RecyclerView
                if (node.className == "androidx.recyclerview.widget.RecyclerView" &&
                    node.viewIdResourceName?.endsWith("recycler_view") == true
                ) found++

                // 그리드 카드 아이템
                if (node.className == "android.widget.FrameLayout" &&
                    node.viewIdResourceName?.endsWith("grid_card_layout_container") == true
                ) found++
            }

            return found >= 3
        }

        // 5. 마이페이지(프로필) 화면 감지
        fun isInstagramProfileScreen(root: AccessibilityNodeInfo): Boolean {
            var found = 0

            root.walkNodes { node ->
                // 프로필 뷰페이저 (게시물/릴스/태그 탭)
                if (node.className == "androidx.viewpager.widget.ViewPager" &&
                    node.viewIdResourceName?.endsWith("profile_viewpager") == true
                ) found++

                // 프로필 탭 아이콘 레이아웃
                if (node.className == "android.widget.HorizontalScrollView" &&
                    node.viewIdResourceName?.endsWith("profile_tab_layout") == true
                ) found++

                // 상단 유저네임 컨테이너
                if (node.className == "android.widget.LinearLayout" &&
                    node.viewIdResourceName?.endsWith("action_bar_username_container") == true
                ) found++
            }

            return found >= 3
        }

        fun isTikTokISScreen(root: AccessibilityNodeInfo): Boolean {
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
        inline fun AccessibilityNodeInfo.walkNodes(visit: (AccessibilityNodeInfo) -> Unit) {
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
}