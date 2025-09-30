package com.example.dontpopyourbrain


import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyService", "접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        val type = event.eventType
        if (pkg == "com.google.android.youtube") {
            Log.d("MyService", "🖐 Youtube 화면 감지 (eventType=$type)")

//            val root = rootInActiveWindow
//            if (root != null) {
//                dumpNode(root, 0)
//            }
        }
    }

    override fun onInterrupt() {
        Log.d("MyService", "서비스 인터럽트됨")
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        val indent = " ".repeat(depth * 2)
        Log.d(
            "MyService",
            "$indent- class=${node.className}, text=${node.text}, contentDesc=${node.contentDescription}, viewId=${node.viewIdResourceName}"
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, depth + 1) }
        }
    }
}