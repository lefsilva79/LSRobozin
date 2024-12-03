package com.example.lsrobozin.apps

import android.view.accessibility.AccessibilityNodeInfo

interface AppMonitor {
    fun searchValues(node: AccessibilityNodeInfo, nodeText: String)
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun setAutoClick(enabled: Boolean)
    fun isAutoClickEnabled(): Boolean
}