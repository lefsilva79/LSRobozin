/*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-06 01:11:49
 * Current User's Login: lefsilva79
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
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lsrobozin.apps.InstacartMonitor

import com.example.lsrobozin.utils.LogHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


interface AppMonitor {
    fun searchValues(rootNode: AccessibilityNodeInfo, text: String)
    fun setEnabled(enabled: Boolean)
}

class MyAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: MyAccessibilityService? = null
        const val MAX_VALUE = 999
        private const val MAX_CLICK_ATTEMPTS = 3
        private const val CLICK_TIMEOUT = 1000L // 1 segundo
        private const val GESTURE_DURATION = 100L // 100ms para gestos
        private const val SEARCH_STATE_ACTION = "com.example.lsrobozin.SEARCH_STATE_CHANGED"

        private lateinit var logHelper: LogHelper
        private val dateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

        fun getInstance(): MyAccessibilityService? {
            return instance
        }
    }

    private lateinit var instacartMonitor: InstacartMonitor
    private lateinit var notificationManager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var targetValue = 0
    private var isSearching = false
    private val foundValues = mutableSetOf<Int>()
    private var allowDuplicates = false
    private var notificationId = 1
    private val TAG = "MyAccessibilityService"
    private val CHANNEL_ID = "ValorLocator"
    private var monitorInstacart = false
    private var instacartAutoClick = false
    private var serviceStarted = false
    private var lastAttemptedClick: Long = 0
    private var clickAttempts = 0
    // Adicione esta linha junto com os outros atributos privados
    private var currentMonitor: AppMonitor? = null

    private val checkStateRunnable = object : Runnable {
        override fun run() {
            if (isSearching) {
                val currentTime = dateFormat.format(Date())
                Log.d(
                    TAG, """
                    [$currentTime] Status atual:
                    Buscando: $isSearching
                    Valor alvo: $targetValue
                    Monitor Instacart: $monitorInstacart
                    Auto-click Instacart: $instacartAutoClick
                    Valores encontrados: ${foundValues.size}
                    Duplicatas permitidas: $allowDuplicates
                """.trimIndent()
                )
                handler.postDelayed(this, 30000) // Verifica a cada 30 segundos
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogHelper.initialize(this)
        instance = this
        createNotificationChannel()
        ensureServiceRunning()

        // Inicializa com valores padrão
        isSearching = false
        targetValue = 0
        foundValues.clear()

        // Inicializa o monitor
        instacartMonitor = InstacartMonitor(this, targetValue, foundValues)

        // Carregar preferências mas NÃO iniciar monitoramento automático
        val prefs = getSharedPreferences("ValorLocator", MODE_PRIVATE)
        monitorInstacart = prefs.getBoolean("monitor_instacart", false)
        instacartAutoClick = prefs.getBoolean("auto_click", false)
        allowDuplicates = prefs.getBoolean("allow_duplicates", false)
        val serviceStarted = prefs.getBoolean("service_started", false)

        // Registra estado inicial do serviço
        LogHelper.logServiceState(this)

        // Adicionar configuração de flags para manter o serviço em execução
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceIntent = Intent(this, MyAccessibilityService::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            LogHelper.logEvent("Configuração de flags Android O+ concluída")
        }

        // Inicia MainActivity se necessário
        if (!serviceStarted) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            LogHelper.logEvent("Primeira execução - MainActivity iniciada")
        }

        // Registra estado final da inicialização
        LogHelper.logServiceState(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacks(checkStateRunnable)

        // Limpa o estado do serviço
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("service_started", false)
            .putBoolean("monitor_instacart", false)
            .putBoolean("auto_click", false)
            .putBoolean("allow_duplicates", false)
            .apply()

        val currentTime = dateFormat.format(Date())
        Log.d(TAG, "[$currentTime] Service Destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ValorLocator"
            val descriptionText = "Notifications for ValorLocator"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setMonitorInstacart(enabled: Boolean) {
        val currentTime = dateFormat.format(Date())
        monitorInstacart = enabled
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("monitor_instacart", enabled)
            .apply()
        Log.d(TAG, "[$currentTime] Monitor Instacart set to: $enabled")
    }

    fun setInstacartAutoClick(enabled: Boolean) {
        val currentTime = dateFormat.format(Date())
        instacartAutoClick = enabled
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("auto_click", enabled)
            .apply()
        Log.d(TAG, "[$currentTime] Instacart AutoClick set to: $enabled")
    }

    fun setAllowDuplicates(enabled: Boolean) {
        val currentTime = dateFormat.format(Date())
        allowDuplicates = enabled
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("allow_duplicates", enabled)
            .apply()
        Log.d(TAG, "[$currentTime] Allow Duplicates set to: $enabled")
    }

    fun getMonitorInstacart(): Boolean = monitorInstacart
    fun getInstacartAutoClick(): Boolean = instacartAutoClick
    fun getAllowDuplicates(): Boolean = allowDuplicates


    fun showNotification(message: String) {
        try {
            val currentTime = dateFormat.format(Date())
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ValorLocator")
                .setContentText("[$currentTime] $message")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText("[$currentTime] $message"))

            with(NotificationManagerCompat.from(this)) {
                if (ActivityCompat.checkSelfPermission(
                        this@MyAccessibilityService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(notificationId++, builder.build())
                }
            }

            Log.d(TAG, "[$currentTime] Notification sent: $message")
        } catch (e: Exception) {
            val currentTime = dateFormat.format(Date())
            Log.e(TAG, "[$currentTime] Error showing notification: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun ensureServiceRunning() {
        val currentTime = dateFormat.format(Date())
        val prefs = getSharedPreferences("ValorLocator", MODE_PRIVATE)
        if (prefs.getBoolean("is_searching", false)) {
            isSearching = true
            targetValue = prefs.getInt("target_value", 0)
            monitorInstacart = prefs.getBoolean("monitor_instacart", false)
            allowDuplicates = prefs.getBoolean("allow_duplicates", false)
            Log.d(
                TAG, """
            [$currentTime] Restored search state:
            Target: $targetValue
            Monitor: $monitorInstacart
            Allow Duplicates: $allowDuplicates
        """.trimIndent()
            )
            handler.post(checkStateRunnable)
        }
    }
// PARTE 1 - FIM

    /*
     * Current Date and Time (UTC): 2024-12-06 01:15:23
     * Current User's Login: lefsilva79
     *
     * Parte 2: Eventos de acessibilidade e gerenciamento de janelas
     */

    // Controle de estado da janela
    private var currentWindow: String? = null
    private var lastWindowChange = 0L
    private val windowCache = mutableMapOf<String, Long>()
    private val WINDOW_CACHE_TIMEOUT = 1000L // 1 segundo

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
        instacartMonitor = InstacartMonitor(
            service = this,
            foundValues = foundValues,
            targetValue = targetValue
        )
    }

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-06 16:05:03
 * Current User's Login: lefsilva79
 */

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-06 16:35:45
 * Current User's Login: lefsilva79
 */

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentTime = dateFormat.format(Date())

        try {
            val packageName = event.packageName?.toString()
            val isRelevantPackage = packageName == "com.example.lsrobozin" ||
                    packageName?.contains("instacart") == true

            LogHelper.logEvent("""
            [$currentTime] Evento Detalhado:
            Tipo: ${event.eventType}
            Package: $packageName
            IsSearching: $isSearching
            TargetValue: $targetValue
            É app relevante?: $isRelevantPackage
        """.trimIndent())

            if (!isSearching || !isRelevantPackage) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    LogHelper.logWindowChange(event, packageName)
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        LogHelper.logEvent("""
                        Processando window state change
                        Package: $packageName
                        Text: ${event.text}
                        Node Class: ${rootNode.className}
                    """.trimIndent())
                        instacartMonitor.searchValues(rootNode, event.text?.toString() ?: "")
                    } else {
                        LogHelper.logEvent("rootNode é null para window state")
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    if (shouldProcessContentChange(event)) {
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            LogHelper.logEvent("""
                            Processando window content change
                            Package: $packageName
                            Text: ${event.text}
                            Node Class: ${rootNode.className}
                        """.trimIndent())
                            instacartMonitor.searchValues(rootNode, event.text?.toString() ?: "")
                        } else {
                            LogHelper.logEvent("rootNode é null para content change")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("Erro em onAccessibilityEvent", e)
            e.printStackTrace()
        }
    }



    // Adicione este novo método
    private fun checkForValues(event: AccessibilityEvent) {
        if (isSearching) {
            LogHelper.logEvent("Verificando valores durante busca ativa")

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                LogHelper.logEvent("Root node encontrado")
                currentMonitor?.let { monitor ->
                    LogHelper.logEvent("Monitor atual: ${monitor.javaClass.simpleName}")
                    monitor.searchValues(rootNode, event.text?.toString() ?: "")
                } ?: LogHelper.logEvent("Monitor atual é nulo")
            } else {
                LogHelper.logEvent("Root node é nulo")
            }
        }
    }


    private fun handleWindowStateChange(event: AccessibilityEvent) {
        val currentTime = dateFormat.format(Date())
        val packageName = event.packageName?.toString()

        if (packageName == null) {
            Log.d(TAG, "[$currentTime] Null package name in window state change")
            return
        }

        // Evita processar a mesma janela múltiplas vezes em um curto período
        val now = System.currentTimeMillis()
        if (currentWindow == packageName && (now - lastWindowChange) < WINDOW_CACHE_TIMEOUT) {
            return
        }

        currentWindow = packageName
        lastWindowChange = now
        windowCache[packageName] = now

        Log.d(
            TAG, """
            [$currentTime] Window state changed
            Package: $packageName
            Class: ${event.className}
            Previous window: ${currentWindow ?: "none"}
            Time since last change: ${now - (windowCache[packageName] ?: now)}ms
        """.trimIndent()
        )

        // Monitor only Instacart for now
        if (packageName == "com.instacart.shopper") {
            val isEnabled = instacartMonitor.isEnabled()
            if (isEnabled) {
                event.source?.let { node ->
                    instacartMonitor.searchValues(node, node.text?.toString() ?: "")
                }
            }
        }
    }

    private fun shouldProcessContentChange(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString() ?: return false
        val isRelevantPackage = packageName == "com.example.lsrobozin" ||
                packageName.contains("instacart")

        if (!isRelevantPackage) {
            return false
        }

        val now = System.currentTimeMillis()
        val lastProcessed = windowCache[packageName] ?: 0L

        LogHelper.logEvent("""
        Verificando processamento:
        Package: $packageName
        É app relevante?: $isRelevantPackage
        Último processamento: ${if (lastProcessed == 0L) "nunca" else "${now - lastProcessed}ms atrás"}
        Timeout configurado: $WINDOW_CACHE_TIMEOUT ms
    """.trimIndent())

        if (now - lastProcessed < WINDOW_CACHE_TIMEOUT) {
            LogHelper.logEvent("Ignorando evento - muito recente")
            return false
        }

        windowCache[packageName] = now
        LogHelper.logEvent("Cache atualizado para $packageName")
        return true
    }

    private fun handleContentChange(event: AccessibilityEvent) {
        LogHelper.logEvent("Handling content change")
        val currentTime = dateFormat.format(Date())
        val packageName = event.packageName?.toString() ?: return

        Log.d(
            TAG, """
            [$currentTime] Content changed
            Package: $packageName
            Source: ${event.source?.className}
            Changes: ${event.contentChangeTypes}
        """.trimIndent()
        )

        // Monitor only Instacart for now
        if (packageName == "com.instacart.shopper") {
            val isEnabled = instacartMonitor.isEnabled()
            if (isEnabled) {
                event.source?.let { node ->
                    instacartMonitor.searchValues(node, node.text?.toString() ?: "")
                }
            }
        }
    }

    private fun handleViewClick(event: AccessibilityEvent) {
        val currentTime = dateFormat.format(Date())
        val packageName = event.packageName?.toString() ?: return

        Log.d(
            TAG, """
            [$currentTime] View clicked
            Package: $packageName
            Class: ${event.className}
            Text: ${event.text?.joinToString() ?: "no text"}
        """.trimIndent()
        )

        // Monitor only Instacart for now
        if (packageName == "com.instacart.shopper") {
            val isEnabled = instacartMonitor.isEnabled()
            if (isEnabled) {
                event.source?.let { node ->
                    instacartMonitor.searchValues(node, node.text?.toString() ?: "")
                }
            }
        }
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
            LogHelper.logError("Erro verificando se node é clicável", e)
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
            LogHelper.logError("Erro verificando se node está na tela", e)
            return false
        }
    }

    /*private fun isNodeClickable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val isNotEditText = node.className?.contains("android.widget.EditText", true)?.not() ?: true

        return node.isClickable &&
                node.isEnabled &&
                node.isVisibleToUser &&
                isNotEditText
    }*/

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
            LogHelper.logError("""
            Erro ao buscar parent clicável
            Node text: ${node.text}
            Node class: ${node.className}
        """.trimIndent(), e)
        }
        return null
    }

    /*private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        val maxDepth = 5
        var depth = 0

        while (current != null && depth < maxDepth) {
            if (isNodeClickable(current)) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }*/
    private fun performClickActions(node: AccessibilityNodeInfo): Boolean {
        try {
            // Primeiro tenta o clique direto
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                LogHelper.logEvent("Clique direto bem sucedido")
                return true
            }

            // Se falhar, tenta dar foco primeiro e depois clicar
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                Thread.sleep(100)
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    LogHelper.logEvent("Clique após foco bem sucedido")
                    return true
                }
            }

            // Se ainda falhar, tenta o clique por gesto
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.width() > 0 && rect.height() > 0) {
                val clickPath = Path()
                clickPath.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())

                val gestureBuilder = GestureDescription.Builder()
                val gestureDescription = gestureBuilder
                    .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 1))
                    .build()

                var gestureResult = false
                dispatchGesture(gestureDescription, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        LogHelper.logEvent("Gesto de clique completado")
                        gestureResult = true
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        LogHelper.logEvent("Gesto de clique cancelado")
                        gestureResult = false
                    }
                }, null)

                Thread.sleep(500) // Espera o gesto completar
                return gestureResult
            }

            return false
        } catch (e: Exception) {
            LogHelper.logError("Erro ao executar ações de clique", e)
            return false
        }
    }

    /*private fun performClickActions(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
    }

    private fun performGestureClick(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        val clickPath = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX, centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, GESTURE_DURATION))
            .build()

        return dispatchGesture(gesture, null, null)
    }*/

    override fun onInterrupt() {
        val currentTime = dateFormat.format(Date())
        Log.d(TAG, "[$currentTime] Service interrupted")
    }

    fun tryClickAndVerify(node: AccessibilityNodeInfo, value: Int): Boolean {
        val startTime = System.currentTimeMillis()
        val currentTime = dateFormat.format(Date())

        LogHelper.logValueDetection(value, node.text?.toString(), value == targetValue)

        LogHelper.log("""
        [$currentTime] Iniciando verificação de clique
        Valor: $value
        Node inicial:
        Text: ${node.text}
        Class: ${node.className}
        ViewId: ${node.viewIdResourceName}
        Bounds: ${node.getBoundsInScreen(Rect())}
        Is Clickable: ${node.isClickable}
        Is Enabled: ${node.isEnabled}
        Is Visible: ${node.isVisibleToUser}
        Parent Class: ${node.parent?.className}
        Child Count: ${node.childCount}
    """.trimIndent())

        if (!isNodeClickable(node)) {
            val clickableParent = findClickableParent(node)
            if (clickableParent == null) {
                LogHelper.logClickAttempt(node, value, "Não Clicável", false)
                LogHelper.log("""
                [$currentTime] Elemento não clicável
                Valor: $value
                Node text: ${node.text}
                Node class: ${node.className}
                Node viewId: ${node.viewIdResourceName}
                Is enabled: ${node.isEnabled}
                Is visible: ${node.isVisibleToUser}
            """.trimIndent())
                showNotification("Valor $value encontrado, mas elemento não é clicável!")
                return false
            }
            LogHelper.log("[$currentTime] Tentando com elemento pai clicável")
            return tryClickAndVerify(clickableParent, value)
        }

        var attempts = 0
        val maxAttempts = MAX_CLICK_ATTEMPTS
        val currentTimeMillis = System.currentTimeMillis()

        if (currentTimeMillis - lastAttemptedClick < CLICK_TIMEOUT) {
            clickAttempts++
            if (clickAttempts >= maxAttempts) {
                LogHelper.logClickAttempt(node, value, "Timeout", false)
                LogHelper.log("""
                [$currentTime] Muitas tentativas em curto período
                Tentativas: $clickAttempts
                Valor: $value
                Timeout: $CLICK_TIMEOUT ms
            """.trimIndent())
                showNotification("Muitas tentativas de clique em $value. Aguardando...")
                return false
            }
        } else {
            clickAttempts = 0
        }
        lastAttemptedClick = currentTimeMillis

        while (attempts < maxAttempts) {
            attempts++
            try {
                LogHelper.log("[$currentTime] Tentativa $attempts de $maxAttempts")

                // Tentativa de clique direto
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Thread.sleep(200)
                    LogHelper.logClickAttempt(node, value, "Direct Click", true)
                    LogHelper.log("""
                    [$currentTime] Clique direto bem sucedido
                    Valor: $value
                    Tentativa: $attempts
                    Tempo total: ${System.currentTimeMillis() - startTime}ms
                """.trimIndent())
                    showNotification("Clique bem sucedido no valor: $value")
                    return true
                }

                // Tenta dar foco primeiro
                if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                    Thread.sleep(100)
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogHelper.logClickAttempt(node, value, "Focus+Click", true)
                        LogHelper.log("""
                        [$currentTime] Clique após foco bem sucedido
                        Valor: $value
                        Tentativa: $attempts
                        Tempo total: ${System.currentTimeMillis() - startTime}ms
                    """.trimIndent())
                        showNotification("Clique bem sucedido no valor: $value")
                        return true
                    }
                }

                // Se ainda não conseguiu, tenta o gesto
                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (rect.width() > 0 && rect.height() > 0) {
                    val clickPath = Path()
                    clickPath.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())

                    val gestureBuilder = GestureDescription.Builder()
                    val gestureDescription = gestureBuilder
                        .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 1))
                        .build()

                    var gestureResult = false
                    dispatchGesture(gestureDescription, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            gestureResult = true
                            LogHelper.logClickAttempt(node, value, "Gesture Click", true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            gestureResult = false
                            LogHelper.logClickAttempt(node, value, "Gesture Cancelled", false)
                        }
                    }, null)

                    Thread.sleep(500)
                    if (gestureResult) {
                        LogHelper.log("""
                        [$currentTime] Clique por gesto bem sucedido
                        Valor: $value
                        Tentativa: $attempts
                        Tempo total: ${System.currentTimeMillis() - startTime}ms
                    """.trimIndent())
                        showNotification("Clique por gesto bem sucedido no valor: $value")
                        return true
                    }
                }

                if (attempts < maxAttempts) {
                    LogHelper.logClickAttempt(node, value, "Tentativa ${attempts}", false)
                    LogHelper.log("[$currentTime] Tentativa $attempts falhou, aguardando 500ms")
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                LogHelper.logError("Tentativa de clique ${attempts}", e)
                LogHelper.log("""
                [$currentTime] Erro na tentativa $attempts
                Valor: $value
                Erro: ${e.message}
                Stack: ${e.stackTraceToString()}
            """.trimIndent())
                if (attempts < maxAttempts) {
                    Thread.sleep(500)
                }
            }
        }

        LogHelper.logClickAttempt(node, value, "Todas tentativas falharam", false)
        LogHelper.log("""
        [$currentTime] Todas as tentativas falharam
        Valor: $value
        Total tentativas: $maxAttempts
        Tempo total: ${System.currentTimeMillis() - startTime}ms
    """.trimIndent())
        showNotification("Falha ao clicar no valor $value após $maxAttempts tentativas")
        return false
    }

    /*fun tryClickAndVerify(node: AccessibilityNodeInfo, value: Int): Boolean {
        val startTime = System.currentTimeMillis()
        val currentTime = dateFormat.format(Date())

        LogHelper.logValueDetection(value, node.text?.toString(), value == targetValue)

        LogHelper.log(
            """
        [$currentTime] Iniciando verificação de clique
        Valor: $value
        Node inicial:
        Text: ${node.text}
        Class: ${node.className}
        ViewId: ${node.viewIdResourceName}
        Bounds: ${node.getBoundsInScreen(Rect())}
        Is Clickable: ${node.isClickable}
        Is Enabled: ${node.isEnabled}
        Is Visible: ${node.isVisibleToUser}
        Parent Class: ${node.parent?.className}
        Child Count: ${node.childCount}
    """.trimIndent()
        )

        if (!isNodeClickable(node)) {
            val clickableParent = findClickableParent(node)
            if (clickableParent == null) {
                LogHelper.logClickAttempt(node, value, "Não Clicável", false)
                LogHelper.log(
                    """
                [$currentTime] Elemento não clicável
                Valor: $value
                Node text: ${node.text}
                Node class: ${node.className}
                Node viewId: ${node.viewIdResourceName}
                Is enabled: ${node.isEnabled}
                Is visible: ${node.isVisibleToUser}
            """.trimIndent()
                )
                showNotification("Valor $value encontrado, mas elemento não é clicável!")
                return false
            }
            LogHelper.log("[$currentTime] Tentando com elemento pai clicável")
            return tryClickAndVerify(clickableParent, value)
        }

        var attempts = 0
        val maxAttempts = MAX_CLICK_ATTEMPTS
        val currentTimeMillis = System.currentTimeMillis()

        if (currentTimeMillis - lastAttemptedClick < CLICK_TIMEOUT) {
            clickAttempts++
            if (clickAttempts >= maxAttempts) {
                LogHelper.logClickAttempt(node, value, "Timeout", false)
                LogHelper.log(
                    """
                [$currentTime] Muitas tentativas em curto período
                Tentativas: $clickAttempts
                Valor: $value
                Timeout: $CLICK_TIMEOUT ms
            """.trimIndent()
                )
                showNotification("Muitas tentativas de clique em $value. Aguardando...")
                return false
            }
        } else {
            clickAttempts = 0
        }
        lastAttemptedClick = currentTimeMillis

        while (attempts < maxAttempts) {
            attempts++
            try {
                LogHelper.log("[$currentTime] Tentativa $attempts de $maxAttempts")

                // Tentativa de clique normal
                if (performClickActions(node)) {
                    Thread.sleep(200)
                    LogHelper.logClickAttempt(node, value, "Normal Click", true)
                    LogHelper.log(
                        """
                    [$currentTime] Clique normal bem sucedido
                    Valor: $value
                    Tentativa: $attempts
                    Tempo total: ${System.currentTimeMillis() - startTime}ms
                """.trimIndent()
                    )
                    showNotification("Clique bem sucedido no valor: $value")
                    return true
                }

                // Se o clique normal falhar, tenta usar gestos
                if (performGestureClick(node)) {
                    Thread.sleep(200)
                    LogHelper.logClickAttempt(node, value, "Gesture Click", true)
                    LogHelper.log(
                        """
                    [$currentTime] Clique por gesto bem sucedido
                    Valor: $value
                    Tentativa: $attempts
                    Tempo total: ${System.currentTimeMillis() - startTime}ms
                """.trimIndent()
                    )
                    showNotification("Clique por gesto bem sucedido no valor: $value")
                    return true
                }

                if (attempts < maxAttempts) {
                    LogHelper.logClickAttempt(node, value, "Tentativa ${attempts}", false)
                    LogHelper.log("[$currentTime] Tentativa $attempts falhou, aguardando 500ms")
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                LogHelper.logError("Tentativa de clique ${attempts}", e)
                LogHelper.log(
                    """
                [$currentTime] Erro na tentativa $attempts
                Valor: $value
                Erro: ${e.message}
                Stack: ${e.stackTraceToString()}
            """.trimIndent()
                )
                if (attempts < maxAttempts) {
                    Thread.sleep(500)
                }
            }
        }

        LogHelper.logClickAttempt(node, value, "Todas tentativas falharam", false)
        LogHelper.log(
            """
        [$currentTime] Todas as tentativas falharam
        Valor: $value
        Total tentativas: $maxAttempts
        Tempo total: ${System.currentTimeMillis() - startTime}ms
    """.trimIndent()
        )
        showNotification("Falha ao clicar no valor $value após $maxAttempts tentativas")
        return false
    }*/
// PARTE 2 - FIM

    /*
     * Current Date and Time (UTC): 2024-12-06 01:16:11
     * Current User's Login: lefsilva79
     *
     * Parte 3: Gerenciamento de estado e monitoramento
     * - Controle do estado de busca
     * - Gestão de valores encontrados
     * - Sistema de broadcast de estado
     * - Métodos de controle de monitoramento
     */

    fun startSearch(value: Int) {
        LogHelper.logEvent("Iniciando busca por valor: $value")

        // Valida o valor
        if (value <= 0 || value > MAX_VALUE) {
            LogHelper.logEvent("Valor inválido para busca: $value")
            return
        }

        // Configura o estado da busca
        targetValue = value
        isSearching = true
        foundValues.clear()
        clickAttempts = 0

        // Inicializa/Atualiza o monitor
        instacartMonitor = InstacartMonitor(this, targetValue, foundValues)
        instacartMonitor.setEnabled(true)

        // Salva o estado nas preferências
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("is_searching", true)
            .putInt("last_value", value)
            .apply()

        LogHelper.logEvent("[${dateFormat.format(Date())}] Broadcasting search state:")
        LogHelper.logEvent("""
        Searching: $isSearching
        Target: $targetValue
        Found: ${foundValues.size}
    """.trimIndent())

        // Atualiza interface e inicia monitoramento
        broadcastSearchState(true)
        showNotification("Buscando valor: $$value")
        handler.post(checkStateRunnable)

        // Registra estado do serviço após início da busca
        LogHelper.logServiceState(this)
    }

    /*
 * MyAccessibilityService.kt
 * Current Date and Time (UTC): 2024-12-06 16:17:36
 * Current User's Login: lefsilva79
 */
    fun stopSearching() {
        val currentTime = dateFormat.format(Date())
        isSearching = false
        targetValue = 0
        foundValues.clear()

        // Remove callbacks pendentes
        handler.removeCallbacks(checkStateRunnable)

        // Limpa o estado
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("is_searching", false)
            .putInt("target_value", 0)
            .apply()

        Log.d(
            TAG, """
        [$currentTime] Search stopped
        Found values count: ${foundValues.size}
        Last window: $currentWindow
    """.trimIndent()
        )

        showNotification("Busca interrompida")
        broadcastSearchState(false)
    }

    fun processFoundValue(value: Int, source: String) {
        val currentTime = dateFormat.format(Date())
        if (!isSearching || value != targetValue) return

        // Verifica duplicatas
        if (!allowDuplicates && foundValues.contains(value)) {
            Log.d(
                TAG, """
                [$currentTime] Duplicate value ignored
                Value: $value
                Source: $source
            """.trimIndent()
            )
            return
        }

        foundValues.add(value)

        Log.d(
            TAG, """
            [$currentTime] Value found
            Value: $value
            Source: $source
            Total found: ${foundValues.size}
            Allow duplicates: $allowDuplicates
        """.trimIndent()
        )

        showNotification("Valor $value encontrado em $source!")

        // Broadcast do valor encontrado
        Intent("com.example.lsrobozin.VALUE_FOUND").also { intent ->
            intent.putExtra("value", value)
            intent.putExtra("source", source)
            intent.putExtra("timestamp", System.currentTimeMillis())
            sendBroadcast(intent)
        }
    }

    fun resetSearch() {
        val currentTime = dateFormat.format(Date())
        foundValues.clear()
        windowCache.clear()
        lastWindowChange = 0L
        currentWindow = null
        clickAttempts = 0
        lastAttemptedClick = 0L

        Log.d(
            TAG, """
            [$currentTime] Search reset
            Target value: $targetValue
            Is searching: $isSearching
        """.trimIndent()
        )

        showNotification("Busca reiniciada")
    }

    fun getSearchStatus(): Triple<Boolean, Int, Set<Int>> {
        return Triple(isSearching, targetValue, foundValues.toSet())
    }


    private fun broadcastSearchState(isSearching: Boolean) {
        val currentTime = dateFormat.format(Date())

        // Criar Intent explícito
            Intent(applicationContext, SearchStateReceiver::class.java).also { intent ->
            intent.action = SEARCH_STATE_ACTION
            intent.putExtra("is_searching", isSearching)
            intent.putExtra("target_value", targetValue)
            intent.putExtra("found_count", foundValues.size)
            intent.putExtra("timestamp", System.currentTimeMillis())
            sendBroadcast(intent)

            LogHelper.logEvent("""
            [$currentTime] Broadcasting search state:
            Searching: $isSearching
            Target: $targetValue
            Found: ${foundValues.size}
        """.trimIndent())
        }
    }


    fun isServiceActive(): Boolean {
        return serviceStarted
    }

    fun setServiceStarted(started: Boolean) {
        val currentTime = dateFormat.format(Date())
        serviceStarted = started
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("service_started", started)
            .apply()

        Log.d(
            TAG, """
            [$currentTime] Service state changed
            Started: $started
            Is searching: $isSearching
            Current window: $currentWindow
        """.trimIndent()
        )
    }

    fun getClickAttempts(): Int {
        return clickAttempts
    }
}