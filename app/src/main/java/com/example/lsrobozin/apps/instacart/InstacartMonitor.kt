package com.example.lsrobozin.apps.instacart

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lsrobozin.MyAccessibilityService
import com.example.lsrobozin.apps.AppMonitor

class InstacartMonitor(
    private val service: MyAccessibilityService,
    private val targetValue: Int,
    private val allowDuplicates: Boolean = false, // Changed default to false
    private val foundValues: MutableSet<Int>
) : AppMonitor {
    private var enabled = false
    private var autoClickEnabled = false

    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        val pattern = """\$\s*(\d{1,3})(?:\.\d{2})?""".toRegex()

        pattern.findAll(nodeText).forEach { match ->
            val rawValue = match.groupValues[1].toIntOrNull() ?: return@forEach

            if (rawValue >= targetValue && rawValue <= MyAccessibilityService.MAX_VALUE) {
                if (foundValues.contains(rawValue)) {
                    service.showNotification("Shopper: $$rawValue - Value previously found.")
                    return@forEach
                }

                if (autoClickEnabled && node.isClickable) {
                    service.showNotification("Shopper: $$rawValue - Trying to click...")

                    if (service.tryClickAndVerify(node, rawValue)) {
                        foundValues.add(rawValue)
                        service.showNotification("Shopper: $$rawValue - Click successfully performed!")
                        // Disables the monitor after a successful click
                        enabled = false
                        service.showNotification("Monitor disabled after successful click.")
                        return
                    } else {
                        service.showNotification("Shopper: $$rawValue - Failed to click! Continuing search...")
                    }
                } else if (!node.isClickable) {
                    service.showNotification("Shopper: $$rawValue - Element is not clickable!")
                }
            }
        }
    }

    override fun isEnabled() = enabled
    override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    override fun setAutoClick(enabled: Boolean) { this.autoClickEnabled = enabled }
    override fun isAutoClickEnabled() = autoClickEnabled
}