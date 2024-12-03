package com.example.lsrobozin.apps.veho

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lsrobozin.MyAccessibilityService
import com.example.lsrobozin.apps.AppMonitor

class VehoMonitor(
    private val service: MyAccessibilityService,
    private val targetValue: Int,
    private val allowDuplicates: Boolean,
    private val foundValues: MutableSet<Int>
) : AppMonitor {
    private var enabled = false
    private var autoClickEnabled = false

    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        val pattern = """\$\s*(\d{1,3})(?:\.\d{2})?(?:-\$\d{1,3}(?:\.\d{2})?)?""".toRegex()

        pattern.findAll(nodeText).forEach { match ->
            val rawValue = match.groupValues[1].toIntOrNull() ?: return@forEach

            if (rawValue >= targetValue && rawValue <= MyAccessibilityService.MAX_VALUE) {
                if (!allowDuplicates && foundValues.contains(rawValue)) {
                    return@forEach
                }

                if (autoClickEnabled && node.isClickable) {
                    if (service.tryClickAndVerify(node, rawValue)) {
                        foundValues.add(rawValue)
                        service.showNotification("Veho: $$rawValue - Clique realizado com sucesso!")
                        return
                    } else {
                        service.showNotification("Veho: $$rawValue - Falha ao clicar! Continuando busca...")
                    }
                } else {
                    foundValues.add(rawValue)
                    service.showNotification("Veho: $$rawValue encontrado!")
                }
            }
        }
    }

    override fun isEnabled() = enabled
    override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    override fun setAutoClick(enabled: Boolean) { this.autoClickEnabled = enabled }
    override fun isAutoClickEnabled() = autoClickEnabled
}