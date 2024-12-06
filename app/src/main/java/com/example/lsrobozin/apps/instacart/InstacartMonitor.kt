/*
 * InstacartMonitor.kt
 * Current Date and Time (UTC): 2024-12-06 05:24:27
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin.apps

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lsrobozin.MyAccessibilityService
import com.example.lsrobozin.utils.LogHelper
import java.util.ArrayDeque
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class InstacartMonitor(
    private val service: MyAccessibilityService,
    private val targetValue: Int,
    private val foundValues: MutableSet<Int>
) : AppMonitor {
    private var enabled = false
    private var autoClickEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TEXTVIEW_CLASS = "android.widget.TextView"
        private const val MAX_DEPTH = 3
        private const val DOLLAR_SIGN = "$"
        private val MONEY_PATTERN = """\$(\d+)(?:\.\d{2})?""".toRegex()
    }

    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        if (!enabled) return

        foundValues.clear()
        val currentTime = dateFormat.format(Date())

        LogHelper.logEvent("""
            [$currentTime] === Nova busca iniciada ===
            Alvo: $targetValue | AutoClick: $autoClickEnabled
            
            === Hierarquia do Node ===
            ${dumpNodeHierarchy(node)}
        """.trimIndent())

        // Busca por TextViews e elementos com $
        val textViews = findNodesByClassName(node, TEXTVIEW_CLASS)
        val dollarNodes = findNodesByText(node, DOLLAR_SIGN, exactMatch = false)

        LogHelper.logEvent("""
            TextViews encontrados: ${textViews.size}
            Nodes com $: ${dollarNodes.size}
        """.trimIndent())

        // Processa todos os nodes encontrados
        (textViews + dollarNodes).distinct().forEach { foundNode ->
            foundNode.text?.toString()?.let { nodeText ->
                searchInText(nodeText, MONEY_PATTERN, foundNode)
            }
        }
    }

    private fun searchInText(text: String, pattern: Regex, node: AccessibilityNodeInfo) {
        try {
            LogHelper.logEvent("""
                Analisando texto: '$text'
                Class: ${node.className}
                ViewId: ${node.viewIdResourceName}
                Clickable: ${node.isClickable}
                Bounds: ${node.getBoundsInScreen(Rect())}
            """.trimIndent())

            pattern.findAll(text).forEach { match ->
                val fullMatch = match.value
                val numericValue = match.groupValues[1]

                LogHelper.logEvent("""
                    Match encontrado:
                    Texto completo: $fullMatch
                    Valor numérico: $numericValue
                """.trimIndent())

                numericValue.toIntOrNull()?.let { value ->
                    LogHelper.logEvent("Valor convertido: $value")
                    processFoundValue(value, node)
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("Erro ao processar: $text", e)
        }
    }

    private fun findNodesByText(
        rootNode: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = true
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        if (rootNode == null) return nodes

        val searchText = text.trim()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()?.trim()

            if (nodeText != null) {
                val matches = if (exactMatch) {
                    nodeText == searchText
                } else {
                    nodeText.contains(searchText, ignoreCase = true)
                }

                if (matches) {
                    nodes.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return nodes
    }

    private fun findNodesByClassName(
        rootNode: AccessibilityNodeInfo?,
        className: String,
        partialMatch: Boolean = false
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        if (rootNode == null) return nodes

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeClassName = node.className?.toString()

            if (nodeClassName != null) {
                val matches = if (partialMatch) {
                    nodeClassName.contains(className, ignoreCase = true)
                } else {
                    nodeClassName == className
                }

                if (matches) {
                    nodes.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return nodes
    }

    private fun dumpNodeHierarchy(
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        maxDepth: Int = 10
    ): String {
        if (node == null || depth > maxDepth) return ""

        val indent = "  ".repeat(depth)
        val builder = StringBuilder()

        builder.append("""
            $indent${node.className}
            ${indent}Text: ${node.text}
            ${indent}Description: ${node.contentDescription}
            ${indent}Id: ${node.viewIdResourceName}
            ${indent}Clickable: ${node.isClickable}
            ${indent}Enabled: ${node.isEnabled}
            ${indent}Visible: ${node.isVisibleToUser}
            ${indent}Bounds: ${node.getBoundsInScreen(Rect())}
        """.trimIndent())

        for (i in 0 until node.childCount) {
            builder.append("\n${dumpNodeHierarchy(node.getChild(i), depth + 1, maxDepth)}")
        }

        return builder.toString()
    }

    private fun processFoundValue(rawValue: Int, node: AccessibilityNodeInfo) {
        val currentTime = dateFormat.format(Date())

        LogHelper.logEvent("""
            [$currentTime] Processando valor encontrado:
            Valor: $rawValue
            Alvo: $targetValue
            Node Info:
            ${dumpNodeHierarchy(node, maxDepth = 2)}
        """.trimIndent())

        if (rawValue >= targetValue && rawValue <= MyAccessibilityService.MAX_VALUE) {
            LogHelper.logEvent("Valor válido encontrado: $rawValue")
            if (autoClickEnabled) {
                handleAutoClick(rawValue, node)
            } else {
                foundValues.add(rawValue)
                service.showNotification("Shopper: $$rawValue encontrado!")
            }
        } else {
            LogHelper.logEvent("Valor descartado: $rawValue (fora dos limites)")
        }
    }

    private fun handleAutoClick(value: Int, node: AccessibilityNodeInfo) {
        LogHelper.logEvent("Tentando auto-click: $value")

        val clickableNode = findClickableParent(node) ?: run {
            LogHelper.logEvent("Nenhum elemento clicável para $value")
            service.showNotification("Shopper: $$value - Elemento não clicável!")
            return
        }

        if (service.tryClickAndVerify(clickableNode, value)) {
            foundValues.add(value)
            service.showNotification("Shopper: $$value - Clicado com sucesso!")
            LogHelper.logEvent("Auto-click bem sucedido: $value")
        } else {
            LogHelper.logEvent("Falha no auto-click: $value")
            service.showNotification("Shopper: $$value - Falha no clique!")
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0

        while (current != null && depth < MAX_DEPTH) {
            if (current.isClickable && current.isEnabled) {
                LogHelper.logEvent("Elemento clicável na profundidade: $depth")
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    override fun isEnabled() = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        LogHelper.logEvent("Monitor ${if (enabled) "habilitado" else "desabilitado"}")
    }

    override fun setAutoClick(enabled: Boolean) {
        this.autoClickEnabled = enabled
        LogHelper.logEvent("Auto-click ${if (enabled) "habilitado" else "desabilitado"}")
    }

    override fun isAutoClickEnabled() = autoClickEnabled
}