/*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-04 03:44:09
 *
 * Parte 1: Configuração inicial e setup do serviço
 * - Imports
 * - Declarações iniciais
 * - Configuração do serviço
 * - Criação de canais de notificação
 * - Métodos de controle básico
 */

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
        const val MAX_VALUE = 999

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


    private fun ensureServiceRunning() {
        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_searching", false)) {
            isSearching = true
            targetValue = prefs.getInt("target_value", 0)
            monitorInstacart = prefs.getBoolean("monitor_instacart", false)
            Log.d(TAG, "Restored search state - Target: $targetValue, Monitor: $monitorInstacart")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        ensureServiceRunning()

        // Inicializa com valores padrão
        isSearching = false
        targetValue = 0
        foundValues.clear()

        // Carregar preferências mas NÃO iniciar monitoramento automático
        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        monitorInstacart = prefs.getBoolean("monitor_instacart", false)
        instacartAutoClick = prefs.getBoolean("auto_click", false)

        // Adicionar configuração de flags para manter o serviço em execução
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceIntent = Intent(this, MyAccessibilityService::class.java)
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Verifica se o serviço deve iniciar a MainActivity
        if (!prefs.getBoolean("service_started", false)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }

        Log.d(TAG, "Service Created - Monitor: $monitorInstacart, AutoClick: $instacartAutoClick")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null

        // Limpa o estado do serviço
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_started", false)
            .putBoolean("monitor_instacart", false)
            .putBoolean("auto_click", false)
            .apply()
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
                Log.d(
                    TAG, """
                    No clickable element found
                    Time: ${formatTimestamp(System.currentTimeMillis())}
                    Value: $value
                    Node text: ${node.text}
                    Node class: ${node.className}
                    Node viewId: ${node.viewIdResourceName}
                    Is enabled: ${node.isEnabled}
                    Is visible: ${node.isVisibleToUser}
                """.trimIndent()
                )
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
                    Log.d(
                        TAG, """
                        Click successful
                        Time: ${formatTimestamp(System.currentTimeMillis())}
                        Value: $value
                        Attempt: $attempts
                        Method: Standard click
                    """.trimIndent()
                    )
                    showNotification("Successfully clicked on value: $value")
                    return true
                }

                if (attempts < maxAttempts) {
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                Log.e(
                    TAG, """
                    Error during click attempt
                    Value: $value
                    Attempt: $attempts
                    Error: ${e.message}
                    Stack trace: ${e.stackTraceToString()}
                """.trimIndent()
                )
            }
        }

        Log.d(
            TAG, """
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
        """.trimIndent()
        )

        showNotification("Failed to click on value: $value after $maxAttempts attempts")
        return false
    }

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-04 03:45:15
 * Current User's Login: lefsilva79
 *
 * Parte 2: Eventos e Buscas
 * - Configuração do serviço
 * - Manipulação de eventos de acessibilidade
 * - Lógica de busca de valores
 * - Processamento de eventos
 */

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - ${formatTimestamp(System.currentTimeMillis())}")

        // Carrega o estado salvo
        ensureServiceRunning()

        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_searching", false)) {
            targetValue = prefs.getInt("target_value", 0)
            isSearching = true
            monitorInstacart = true

            // Notifica a MainActivity que ainda está em busca
            broadcastSearchState(true)

            Log.d(TAG, """
            Restored search state:
            Target Value: $targetValue
            Is Searching: $isSearching
            Monitor Instacart: $monitorInstacart
            Time: ${formatTimestamp(System.currentTimeMillis())}
        """.trimIndent())
        }

        // Inicializa os monitores
        instacartMonitor = InstacartMonitor(
            service = this,
            foundValues = foundValues,
            targetValue = targetValue
        )

        // Configura logs detalhados para debug
        Log.d(TAG, """
        Service fully connected:
        Date/Time: ${formatTimestamp(System.currentTimeMillis())}
        User: lefsilva79
        Is Searching: $isSearching
        Target Value: $targetValue
        Monitor Instacart: $monitorInstacart
        Auto Click: $instacartAutoClick
    """.trimIndent())

        // Verifica se deve iniciar a MainActivity
        if (!prefs.getBoolean("service_started", false)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } else {
            // Se o serviço já estava iniciado e em busca, notifica
            if (isSearching) {
                showNotification("Serviço reconectado - Continuando busca pelo valor: $targetValue")
            }
        }
    }
    fun setServiceStarted(started: Boolean) {
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_started", started)
            .apply()
    }


    // PARTE 2 - modificação no método onAccessibilityEvent
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            if (isSearching) {
                // Log para debug
                Log.d(TAG, """
                Event received:
                Type: ${event.eventType}
                Package: ${event.packageName}
                isSearching: $isSearching
                monitorInstacart: $monitorInstacart
            """.trimIndent())

                // Processa todos os tipos de eventos
                getRootInActiveWindow()?.let { rootNode ->
                    try {
                        // Verifica se estamos no Instacart ou se devemos processar outros apps
                        val packageName = rootNode.packageName?.toString()
                        Log.d(TAG, "Current package: $packageName")

                        when (packageName) {
                            "com.instacart.shopper" -> {
                                Log.d(TAG, "Processing Instacart window")
                                searchAndClickValue(rootNode, targetValue)
                            }
                            else -> {
                                Log.d(TAG, "Skipping non-Instacart package")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing node: ${e.message}")
                    } finally {
                        rootNode.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in accessibility event", e)
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    private fun broadcastSearchState(isSearching: Boolean) {
        val intent = Intent("com.example.lsrobozin.SEARCH_STATE_CHANGED").apply {
            putExtra("is_searching", isSearching)
            putExtra("target_value", targetValue)
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("monitor_instacart", monitorInstacart)
            putExtra("auto_click", instacartAutoClick)
        }
        sendBroadcast(intent)
        Log.d(TAG, """
        Broadcast search state:
        State: $isSearching
        Target: $targetValue
        Time: ${formatTimestamp(System.currentTimeMillis())}
        Monitor: $monitorInstacart
        AutoClick: $instacartAutoClick
    """.trimIndent())
    }

    fun startSearching(value: Int, allowDuplicateClicks: Boolean = false) {
        if (value > MAX_VALUE) {
            showNotification("Valor $value é maior que o máximo permitido ($MAX_VALUE)")
            return
        }

        Log.d(TAG, "Starting search for value: $value")

        targetValue = value
        allowDuplicates = true  // Força permitir duplicados
        isSearching = true
        foundValues.clear()

        // Persiste o estado da busca
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_searching", true)
            .putInt("target_value", value)
            .putBoolean("monitor_instacart", true)
            .apply()

        // Força a ativação do monitoramento
        monitorInstacart = true

        // Notifica a MainActivity
        broadcastSearchState(true)

        showNotification("Iniciando busca contínua pelo valor: $value")
        Log.d(TAG, "Continuous search started - Target: $value, Monitor: $monitorInstacart")
    }

    fun stopSearching() {
        isSearching = false

        // Limpa o estado de busca persistido
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_searching", false)
            .remove("target_value")
            .apply()

        // Notifica a MainActivity
        broadcastSearchState(false)

        Log.d(TAG, "Stopped continuous search")
        showNotification("Busca contínua interrompida")
    }

    private fun searchAndClickValue(node: AccessibilityNodeInfo?, targetValue: Int) {
        if (node == null) return

        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(node)

            while (queue.isNotEmpty() && isSearching) {
                val currentNode = queue.removeFirst()

                if (!isNotification(currentNode)) {
                    val nodeText = currentNode.text?.toString() ?: ""

                    if (nodeText.matches(Regex("""\d+"""))) {
                        val value = extractNumericValue(nodeText)
                        if (value == targetValue) {  // Removida a verificação de duplicados
                            Log.d(TAG, "Found target value: $value")
                            // Não adiciona ao foundValues para permitir múltiplos cliques
                            if (instacartAutoClick) {
                                performClickActions(currentNode)
                                // Não retorna após o clique, continua procurando
                            }
                            showNotification("Valor $value encontrado!")
                        }
                    }

                    // Continue procurando em todos os nós filhos
                    for (i in 0 until currentNode.childCount) {
                        currentNode.getChild(i)?.let { queue.add(it) }
                    }
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
                    viewId.contains("toast", true)
                ) {
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

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-04 03:46:09
 * Current User's Login: lefsilva79
 *
 * Parte 3: Utilitários e Verificações de Nodos
 * - Formatação de timestamp
 * - Captura de estado da janela
 * - Verificações de nodos
 * - Utilitários de acessibilidade
 */

    private fun formatTimestamp(timestamp: Long): String {
        val sdf =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
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
            Log.e(
                TAG, """
                Error finding clickable parent
                Node text: ${node.text}
                Node class: ${node.className}
                Error: ${e.message}
            """.trimIndent()
            )
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

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-04 03:46:53
 * Current User's Login: lefsilva79
 *
 * Parte 4: Ações de Clique e Notificações
 * - Execução de cliques
 * - Gestos personalizados
 * - Notificações ao usuário
 * - Finalização da classe
 */

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
                val targetY = (rect.centerY() + screenMiddleY) / 2f  // <- Modificação feita aqui

                val path = Path().apply {
                    moveTo(startX, targetY)
                }

                val gestureDescription = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                    .build()

                dispatchGesture(
                    gestureDescription,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.d(TAG, "CLICK_TYPE: Gesture click successful")
                            showNotificationToUser("Clique por gesto bem sucedido!")
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.d(TAG, "CLICK_TYPE: Gesture click cancelled")
                            showNotificationToUser("Clique por gesto cancelado!")
                        }
                    },
                    null
                )

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
// Fim da classe MyAccessibilityService