package com.example.arguewithai


import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {
    private var isShort = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyService", "접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        val type = event.eventType
        if (pkg == "com.google.android.youtube") {
            //Log.d("MyService", "🖐 Youtube 화면 감지 (eventType=$type)")

            val root = rootInActiveWindow
            if (isShortsScreen(root) && !isShort) {
                Log.d("MyService", "✅ Shorts 시청 시작")
                isShort = true
            } else if(!isShortsScreen(root) && isShort) {
                Log.d("MyService", "✅ Shorts 시청 종료")
                isShort = false
            }
        } else {
            isShort = false
        }
    }

    override fun onInterrupt() {
        Log.d("MyService", "서비스 인터럽트됨")
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
    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean {
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