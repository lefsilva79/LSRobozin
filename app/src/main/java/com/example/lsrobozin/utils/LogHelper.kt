/*
 * LogHelper.kt
 * Current Date and Time (UTC): 2024-12-05 03:52:01
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin.utils

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lsrobozin.MyAccessibilityService
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LogHelper {
    private const val TAG = "LogHelper"
    private var fileLogger: FileLogger? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun initialize(context: Context) {
        fileLogger = FileLogger.getInstance(context)
        log("=== Log Helper Initialized ===")
    }

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message"
        fileLogger?.log(logMessage)
        Log.d(TAG, logMessage)
    }

    fun logEvent(message: String) {
        log("EVENT: $message")
    }

    fun logError(context: String, error: Throwable) {
        log("""
            ERROR in $context
            Message: ${error.message}
            Stack: ${error.stackTraceToString()}
        """.trimIndent())
    }

    fun logWindowChange(event: AccessibilityEvent, packageName: String?) {
        log("""
            Window Changed:
            Package: $packageName
            Event Class: ${event.className}
            Event Type: ${event.eventType}
            Event Time: ${event.eventTime}
        """.trimIndent())
    }

    fun logValueDetection(value: Int, nodeText: String?, isTarget: Boolean) {
        log("""
            Value Detected:
            Value: $value
            Node Text: $nodeText
            Is Target: $isTarget
        """.trimIndent())
    }

    fun logClickAttempt(node: AccessibilityNodeInfo, value: Int, clickType: String, success: Boolean) {
        log("""
            Click Attempt:
            Value: $value
            Click Type: $clickType
            Success: $success
            Node Info:
            - Text: ${node.text}
            - Class: ${node.className}
            - Clickable: ${node.isClickable}
            - Enabled: ${node.isEnabled}
            - Visible: ${node.isVisibleToUser}
        """.trimIndent())
    }

    /*
 * LogHelper.kt
 * Current Date and Time (UTC): 2024-12-05 03:53:44
 * Current User's Login: lefsilva79
 */

    /*
 * LogHelper.kt
 * Current Date and Time (UTC): 2024-12-05 03:56:16
 * Current User's Login: lefsilva79
 */

    fun logServiceState(service: MyAccessibilityService) {
        log("""
        Service State:
        Active state:
        - Service started: ${service.isServiceActive()}
        - Is searching: ${service.getSearchStatus().first}
        - Target value: ${service.getSearchStatus().second}
        - Found values: ${service.getSearchStatus().third.size}
        - Allow duplicates: ${service.getAllowDuplicates()}
        
        Monitor state:
        - Instacart monitor: ${service.getMonitorInstacart()}
        - Instacart auto-click: ${service.getInstacartAutoClick()}
        
        Click state:
        - Click attempts: ${service.getClickAttempts()}
    """.trimIndent())
    }

    fun getLogFilePath(): String? {
        return fileLogger?.getLogFilePath()
    }

    fun clearLogs() {
        fileLogger?.clearLogs()
        log("=== Logs Cleared ===")
    }
}