package com.example.lsrobozin.apps.instacart

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lsrobozin.MyAccessibilityService
import com.example.lsrobozin.apps.AppMonitor

class InstacartMonitor(
    private val service: MyAccessibilityService,
    private val targetValue: Int,
    private val allowDuplicates: Boolean = false,
    private val foundValues: MutableSet<Int>
) : AppMonitor {
    private var enabled = false
    private var autoClickEnabled = false
    private val TAG = "InstacartMonitor"

    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        val pattern = """\$\s*(\d{1,3})(?:\.\d{2})?""".toRegex()

        Log.d(TAG, "Searching in text: $nodeText")

        pattern.findAll(nodeText).forEach { match ->
            val rawValue = match.groupValues[1].toIntOrNull() ?: return@forEach

            Log.d(TAG, "Found value: $rawValue, Target: $targetValue")

            if (rawValue >= targetValue && rawValue <= MyAccessibilityService.MAX_VALUE) {
                if (foundValues.contains(rawValue) && !allowDuplicates) {
                    Log.d(TAG, "Value $rawValue already found")
                    service.showNotification("Shopper: $$rawValue - Valor já encontrado anteriormente.")
                    return@forEach
                }

                if (autoClickEnabled && node.isClickable) {
                    Log.d(TAG, "Attempting to click on value: $rawValue")
                    service.showNotification("Shopper: $$rawValue - Tentando clicar...")

                    if (service.tryClickAndVerify(node, rawValue)) {
                        foundValues.add(rawValue)
                        service.showNotification("Shopper: $$rawValue - Clique realizado com sucesso!")
                        // Não desativa mais o monitoramento aqui
                        return
                    } else {
                        Log.d(TAG, "Click failed for value: $rawValue")
                        service.showNotification("Shopper: $$rawValue - Falha ao clicar! Continuando busca...")
                    }
                } else {
                    if (!node.isClickable) {
                        Log.d(TAG, "Non-clickable element found with value: $rawValue")
                        service.showNotification("Shopper: $$rawValue - Elemento não é clicável!")
                    }
                    foundValues.add(rawValue)
                    service.showNotification("Shopper: $$rawValue encontrado!")
                }
            }
        }
    }

    override fun isEnabled() = enabled
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "Monitor enabled set to: $enabled")
    }
    override fun setAutoClick(enabled: Boolean) {
        this.autoClickEnabled = enabled
        Log.d(TAG, "AutoClick enabled set to: $enabled")
    }
    override fun isAutoClickEnabled() = autoClickEnabled
}