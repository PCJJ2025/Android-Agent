package com.floatagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.floatagent.model.ScreenData

class ScreenReaderService : AccessibilityService() {

    companion object {
        var instance: ScreenReaderService? = null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // 主动调用：抓取当前屏幕完整数据
    fun getCurrentScreenData(): ScreenData {
        val root = rootInActiveWindow ?: return ScreenData.empty()
        val packageName = root.packageName?.toString() ?: ""
        val texts = mutableListOf<String>()
        val clickableTexts = mutableListOf<String>()

        collectNodes(root, texts, clickableTexts)

        return ScreenData(
            packageName = packageName,
            allTexts = texts,
            clickableTexts = clickableTexts
        )
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        clickableTexts: MutableList<String>
    ) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (!text.isNullOrBlank()) {
            texts.add(text)
            if (node.isClickable) clickableTexts.add(text)
        }
        if (!desc.isNullOrBlank() && desc != text) {
            texts.add(desc)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodes(it, texts, clickableTexts) }
        }
    }
}
