package com.example.lsrobozin

import android.graphics.Rect
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lsrobozin.apps.AppMonitor
import com.example.lsrobozin.apps.instacart.InstacartMonitor
import com.example.lsrobozin.apps.veho.VehoMonitor

class MyAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: MyAccessibilityService? = null
        const val MAX_VALUE = 999 // Valor máximo permitido para busca (3 dígitos)

        fun getInstance(): MyAccessibilityService? {
            return instance
        }
    }

    private lateinit var instacartMonitor: InstacartMonitor
    private var targetValue = 0
    private var isSearching = false
    private val foundValues = mutableSetOf<Int>()
    private var allowDuplicates = false
    private var notificationId = 1
    private val TAG = "MyAccessibilityService"
    private val CHANNEL_ID = "ValorLocator"
    private var monitorInstacart = false
    private var instacartAutoClick = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Carregar preferências
        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        monitorInstacart = prefs.getBoolean("monitor_instacart", false)
        instacartAutoClick = prefs.getBoolean("auto_click", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ValorLocator"
            val descriptionText = "Notifications for ValorLocator"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setMonitorInstacart(enabled: Boolean) {
        monitorInstacart = enabled
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("monitor_instacart", enabled)
            .apply()
    }

    fun setInstacartAutoClick(enabled: Boolean) {
        instacartAutoClick = enabled
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_click", enabled)
            .apply()
    }

    fun getMonitorInstacart(): Boolean {
        return monitorInstacart
    }

    fun getInstacartAutoClick(): Boolean {
        return instacartAutoClick
    }

    fun showNotification(message: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ValorLocator")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            with(NotificationManagerCompat.from(this)) {
                if (ActivityCompat.checkSelfPermission(
                        this@MyAccessibilityService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(notificationId++, builder.build())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}")
        }
    }

    fun tryClickAndVerify(node: AccessibilityNodeInfo, value: Int): Boolean {
        if (!isNodeClickable(node)) {
            val clickableParent = findClickableParent(node)
            if (clickableParent == null) {
                Log.d(TAG, """
                    No clickable element found
                    Time: ${formatTimestamp(System.currentTimeMillis())}
                    Value: $value
                    Node text: ${node.text}
                    Node class: ${node.className}
                    Node viewId: ${node.viewIdResourceName}
                    Is enabled: ${node.isEnabled}
                    Is visible: ${node.isVisibleToUser}
                """.trimIndent())
                return false
            }
            return tryClickAndVerify(clickableParent, value)
        }

        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            attempts++
            try {
                if (performClickActions(node)) {
                    Thread.sleep(200)
                    Log.d(TAG, """
                        Click successful
                        Time: ${formatTimestamp(System.currentTimeMillis())}
                        Value: $value
                        Attempt: $attempts
                        Method: Standard click
                    """.trimIndent())
                    showNotification("Successfully clicked on value: $value")
                    return true
                }

                if (attempts < maxAttempts) {
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, """
                    Error during click attempt
                    Value: $value
                    Attempt: $attempts
                    Error: ${e.message}
                    Stack trace: ${e.stackTraceToString()}
                """.trimIndent())
            }
        }

        Log.d(TAG, """
            Failed to click after all attempts
            Time: ${formatTimestamp(System.currentTimeMillis())}
            Value: $value
            Node details:
            Text: ${node.text}
            Class: ${node.className}
            ViewId: ${node.viewIdResourceName}
            Clickable: ${node.isClickable}
            Enabled: ${node.isEnabled}
            Visible: ${node.isVisibleToUser}
        """.trimIndent())

        showNotification("Failed to click on value: $value after $maxAttempts attempts")
        return false
    }
// PARTE 1 - FIM

    // PARTE 2 - INÍCIO
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
        instacartMonitor = InstacartMonitor(
            service = this,
            foundValues = foundValues,
            targetValue = targetValue
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isSearching) return

        try {
            // Corrigido o package name para o Instacart Shopper
            if (event.packageName?.toString() != "com.instacart.shopper") {
                return
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    if (monitorInstacart) {
                        event.source?.let { rootNode ->
                            searchAndClickValue(rootNode, targetValue)
                            rootNode.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    fun startSearching(value: Int, allowDuplicateClicks: Boolean = false) {
        if (value > MAX_VALUE) {
            showNotification("Valor $value é maior que o máximo permitido ($MAX_VALUE)")
            return
        }
        targetValue = value
        allowDuplicates = allowDuplicateClicks
        isSearching = true
        foundValues.clear()
        Log.d(TAG, "Started searching for value: $value")
    }

    fun stopSearching() {
        isSearching = false
        Log.d(TAG, "Stopped searching")
    }

    private fun searchAndClickValue(node: AccessibilityNodeInfo?, targetValue: Int) {
        if (node == null || !monitorInstacart) return

        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(node)

            while (queue.isNotEmpty()) {
                val currentNode = queue.removeFirst()

                // Verifica se NÃO é uma notificação antes de processar
                if (!isNotification(currentNode)) {
                    val nodeText = currentNode.text?.toString() ?: ""

                    // Verifica se o formato é um número inteiro
                    if (nodeText.matches(Regex("""\d+"""))) { // Ajustado para número inteiro
                        val value = extractNumericValue(nodeText)
                        if (value == targetValue && (allowDuplicates || !foundValues.contains(value))) {
                            foundValues.add(value)
                            if (instacartAutoClick) {
                                performClickActions(currentNode)
                            }
                        }
                    }
                }

                for (i in 0 until currentNode.childCount) {
                    currentNode.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for value: ${e.message}")
        }
    }

    private fun isNotification(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        try {
            var current = node
            var depth = 0
            val maxDepth = 5

            while (current != null && depth < maxDepth) {
                val className = current.className?.toString() ?: ""
                val viewId = current.viewIdResourceName ?: ""

                if (className.contains("Notification", true) ||
                    className.contains("Toast", true) ||
                    viewId.contains("notification", true) ||
                    viewId.contains("status_bar", true) ||
                    viewId.contains("toast", true)) {
                    return true
                }

                val parent = current.parent
                if (current != node) {
                    current.recycle()
                }
                current = parent
                depth++
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification: ${e.message}")
            return true
        }
    }

    private fun extractNumericValue(text: String): Int {
        return try {
            text.filter { it.isDigit() }.toInt()
        } catch (e: Exception) {
            -1
        }
    }
// PARTE 2 - FIM

    // PARTE 3 - INÍCIO
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private var rootInActiveWindow: String? = null
        get() {
            return try {
                val root = super.getRootInActiveWindow()
                root?.let {
                    val builder = StringBuilder()
                    captureWindowState(it, builder)
                    it.recycle()
                    builder.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting root window", e)
                null
            }
        }

    private fun captureWindowState(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        try {
            builder.append(node.text ?: "")
            builder.append(node.contentDescription ?: "")

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    captureWindowState(child, builder)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing window state", e)
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        try {
            var current = node
            var maxAttempts = 5
            var previousNode: AccessibilityNodeInfo? = null

            while (current != null && maxAttempts > 0) {
                if (isNodeClickable(current)) {
                    if (isNodeOnScreen(current)) {
                        return current
                    }
                }
                previousNode = current
                current = current.parent
                if (current != previousNode) {
                    previousNode.recycle()
                }
                maxAttempts--
            }
        } catch (e: Exception) {
            Log.e(TAG, """
                Error finding clickable parent
                Node text: ${node.text}
                Node class: ${node.className}
                Error: ${e.message}
            """.trimIndent())
        }
        return null
    }

    private fun isNodeClickable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        return try {
            node.isClickable ||
                    (node.isEnabled && (
                            node.className?.toString()?.lowercase()?.let { className ->
                                className.contains("button") ||
                                        className.contains("textview") ||
                                        className.contains("imageview") ||
                                        className.contains("linearlayout") ||
                                        className.contains("relativelayout") ||
                                        className.contains("cardview")
                            } == true ||
                                    node.viewIdResourceName?.lowercase()?.let { id ->
                                        id.contains("btn") ||
                                                id.contains("button") ||
                                                id.contains("click") ||
                                                id.contains("select") ||
                                                id.contains("card")
                                    } == true
                            )) &&
                    !node.isScrollable &&
                    node.isVisibleToUser &&
                    isNodeOnScreen(node)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if node is clickable: ${e.message}")
            false
        }
    }

    private fun isNodeOnScreen(node: AccessibilityNodeInfo): Boolean {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            return rect.left >= 0 &&
                    rect.top >= 0 &&
                    rect.right <= screenWidth &&
                    rect.bottom <= screenHeight &&
                    rect.width() > 0 &&
                    rect.height() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if node is on screen: ${e.message}")
            return false
        }
    }
// PARTE 3 - FIM

    // PARTE 4 - INÍCIO
    private fun performClickActions(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        try {
            // Tenta o clique normal primeiro
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "CLICK_TYPE: Normal click successful")
                showNotificationToUser("Clique normal bem sucedido!")
                return true
            }

            // Se o clique normal falhar, tenta o clique por gestos
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.width() > 0 && rect.height() > 0) {
                val displayMetrics = resources.displayMetrics
                val screenMiddleY = displayMetrics.heightPixels / 2

                val startX = rect.centerX().toFloat()
                val targetY = (rect.centerY() + screenMiddleY) / 2

                val path = Path().apply {
                    moveTo(startX, targetY)
                }

                val gestureDescription = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                    .build()

                dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "CLICK_TYPE: Gesture click successful")
                        showNotificationToUser("Clique por gesto bem sucedido!")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "CLICK_TYPE: Gesture click cancelled")
                        showNotificationToUser("Clique por gesto cancelado!")
                    }
                }, null)

                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click: ${e.message}")
            showNotificationToUser("Erro ao tentar clicar: ${e.message}")
        }
        return false
    }

    private fun showNotificationToUser(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
// PARTE 4 - FIM