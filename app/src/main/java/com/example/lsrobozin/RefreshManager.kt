package com.example.lsrobozin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class RefreshManager private constructor(private val service: MyAccessibilityService) {
    companion object {
        private const val TAG = "RefreshManager"
        private const val REFRESH_INTERVAL = 5000L // 5 segundos
        private const val INITIAL_DELAY = 10000L   // 10 segundos
        private var instance: RefreshManager? = null

        fun getInstance(service: MyAccessibilityService): RefreshManager {
            if (instance == null) {
                instance = RefreshManager(service)
            }
            return instance!!
        }
    }

    private var isRefreshEnabled = false
    private var isRefreshGestureInProgress = false
    private var lastRefreshTime = 0L
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    fun startRefresh() {
        if (isRefreshEnabled) return

        // Verifica se é o app Instacart
        if (!isInstacartActive()) {
            logAndNotify("Refresh não iniciado: App Instacart não está ativo")
            return
        }

        isRefreshEnabled = true
        Log.d(TAG, "Iniciando refresh em 10 segundos...")

        // Delay inicial de 10 segundos
        refreshHandler.postDelayed({
            refreshRunnable = object : Runnable {
                override fun run() {
                    if (isRefreshEnabled && isInstacartActive()) {
                        performRefresh()
                        refreshHandler.postDelayed(this, REFRESH_INTERVAL)
                    } else if (!isInstacartActive()) {
                        stopRefresh()
                    }
                }
            }
            refreshHandler.post(refreshRunnable!!)
            logAndNotify("Refresh iniciado")
        }, INITIAL_DELAY)
    }

    fun stopRefresh() {
        if (!isRefreshEnabled) return

        isRefreshEnabled = false
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
        logAndNotify("Refresh parado")
    }

    private fun isInstacartActive(): Boolean {
        val rootNode = service.getRootInActiveWindow()
        val isActive = rootNode?.packageName?.toString() == "com.instacart.shopper"
        rootNode?.recycle()
        return isActive
    }

    private fun performRefresh() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastRefreshTime < 1000) {
            Log.d(TAG, "Ignorando refresh - muito cedo")
            return
        }

        if (isRefreshGestureInProgress) {
            Log.d(TAG, "Ignorando refresh - gesto em andamento")
            return
        }

        try {
            isRefreshGestureInProgress = true
            lastRefreshTime = currentTime

            val displayMetrics = service.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val path = Path().apply {
                moveTo(screenWidth / 2f, screenHeight * 0.2f)
                lineTo(screenWidth / 2f, screenHeight * 0.6f)
            }

            val gestureDescription = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()

            service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    isRefreshGestureInProgress = false
                    Log.d(TAG, "Refresh completado em ${formatTimestamp(currentTime)}")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    isRefreshGestureInProgress = false
                    Log.d(TAG, "Refresh cancelado")
                }
            }, null)

            refreshHandler.postDelayed({
                if (isRefreshGestureInProgress) {
                    isRefreshGestureInProgress = false
                    Log.d(TAG, "Timeout do refresh - resetando flag")
                }
            }, 1000)

        } catch (e: Exception) {
            isRefreshGestureInProgress = false
            Log.e(TAG, "Erro no refresh: ${e.message}")
        }
    }

    fun isEnabled(): Boolean = isRefreshEnabled

    fun cleanup() {
        stopRefresh()
        instance = null
    }

    private fun logAndNotify(message: String) {
        Log.d(TAG, message)
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}