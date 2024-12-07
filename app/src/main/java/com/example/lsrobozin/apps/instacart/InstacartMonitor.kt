/*
 * InstacartMonitor.kt
 * Current Date and Time (UTC): 2024-12-06 15:53:24
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
    private val targetValue: Int,  // Este é o valor mínimo que queremos encontrar
    private val foundValues: MutableSet<Int>
) : AppMonitor {
    private var enabled = false
    private var autoClickEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TEXTVIEW_CLASS = "android.widget.TextView"
        private const val EDITTEXT_CLASS = "android.widget.EditText"
        private const val MAX_DEPTH = 3
        private const val DOLLAR_SIGN = "$"
        private val MONEY_PATTERN = """\$(\d+)(?:\.\d{2})?""".toRegex()
    }

    /*
 * InstacartMonitor.kt
 * Current Date and Time (UTC): 2024-12-06 16:37:22
 * Current User's Login: lefsilva79
 */

    /*
 * InstacartMonitor.kt
 * Current Date and Time (UTC): 2024-12-06 16:47:10
 * Current User's Login: lefsilva79
 */

    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        // Verifica se é o Instacart
        val packageName = node.packageName?.toString()
        if (!packageName?.contains("instacart", ignoreCase = true)!!) {
            LogHelper.logEvent("Ignorando package que não é Instacart: $packageName")
            return
        }

        LogHelper.log("""
        === INÍCIO DO SEARCHVALUES ===
        Monitor enabled: $enabled
        Target Value: $targetValue
        Node Package: ${node.packageName}
        Node Class: ${node.className}
        Node Text: $nodeText
        AutoClick: $autoClickEnabled
    """.trimIndent())

        if (!enabled) {
            LogHelper.logEvent("Monitor está desabilitado, retornando...")
            return
        }

        foundValues.clear()
        val currentTime = dateFormat.format(Date())

        // Testa o node recebido diretamente
        node.text?.toString()?.let { text ->
            LogHelper.logEvent("Analisando texto do node principal: '$text'")
            if (text.contains("$")) {
                LogHelper.logEvent("$ encontrado no node principal: $text")
                searchInText(text, MONEY_PATTERN, node)
            }
        }

        // Busca em todos os children
        val textViews = findNodesByClassName(node, TEXTVIEW_CLASS)
        val editTexts = findNodesByClassName(node, EDITTEXT_CLASS)

        LogHelper.log("""
        Elementos encontrados:
        TextViews: ${textViews.size}
        EditTexts: ${editTexts.size}
        Package: ${node.packageName}
    """.trimIndent())

        (textViews + editTexts).forEach { foundNode ->
            foundNode.text?.toString()?.let { text ->
                LogHelper.logEvent("Analisando elemento: '$text'")
                if (text.contains("$") && text.indexOf("$").let { i ->
                        i < text.length - 1 && text[i + 1].isDigit()
                    }) {
                    LogHelper.logEvent("Padrão $ + número encontrado: $text")
                    searchInText(text, MONEY_PATTERN, foundNode)
                }
            }
        }
    }

    /* VERSÃO ANTERIOR (permite detecção em qualquer app)
    override fun searchValues(node: AccessibilityNodeInfo, nodeText: String) {
        LogHelper.log("""
            === INÍCIO DO SEARCHVALUES ===
            Monitor enabled: $enabled
            Target Value: $targetValue
            Node Package: ${node.packageName}
            Node Class: ${node.className}
            Node Text: $nodeText
            AutoClick: $autoClickEnabled
        """.trimIndent())

        if (!enabled) {
            LogHelper.logEvent("Monitor está desabilitado, retornando...")
            return
        }

        foundValues.clear()
        val currentTime = dateFormat.format(Date())

        // Testa o node recebido diretamente
        node.text?.toString()?.let { text ->
            LogHelper.logEvent("Analisando texto do node principal: '$text'")
            if (text.contains("$")) {
                LogHelper.logEvent("$ encontrado no node principal: $text")
                searchInText(text, MONEY_PATTERN, node)
            }
        }

        // Busca em todos os children
        val textViews = findNodesByClassName(node, TEXTVIEW_CLASS)
        val editTexts = findNodesByClassName(node, EDITTEXT_CLASS)

        LogHelper.log("""
            Elementos encontrados:
            TextViews: ${textViews.size}
            EditTexts: ${editTexts.size}
            Package: ${node.packageName}
        """.trimIndent())

        (textViews + editTexts).forEach { foundNode ->
            foundNode.text?.toString()?.let { text ->
                LogHelper.logEvent("Analisando elemento: '$text'")
                if (text.contains("$") && text.indexOf("$").let { i ->
                        i < text.length - 1 && text[i + 1].isDigit()
                    }) {
                    LogHelper.logEvent("Padrão $ + número encontrado: $text")
                    searchInText(text, MONEY_PATTERN, foundNode)
                }
            }
        }
    }
    */

    private fun searchInText(text: String, pattern: Regex, node: AccessibilityNodeInfo) {
        try {
            LogHelper.log("""
            === ANÁLISE DETALHADA DE TEXTO ===
            Texto: '$text'
            Package: ${node.packageName}
            Class: ${node.className}
            ViewId: ${node.viewIdResourceName}
            Clickable: ${node.isClickable}
            Enabled: ${node.isEnabled}
            Visible: ${node.isVisibleToUser}
            Procurando valores >= $targetValue
        """.trimIndent())

            pattern.findAll(text).forEach { match ->
                LogHelper.logEvent("Match encontrado: '${match.value}'")

                if (match.value.startsWith("$") && match.value[1].isDigit()) {
                    val numericValue = match.groupValues[1]
                    LogHelper.logEvent("Valor extraído: $numericValue")

                    numericValue.toIntOrNull()?.let { value ->
                        LogHelper.logValueDetection(value, text, value >= targetValue)

                        if (value >= targetValue) {
                            LogHelper.logEvent("Valor válido encontrado: $value (>= $targetValue)")
                            processFoundValue(value, node)
                        } else {
                            LogHelper.logEvent("Valor ignorado: $value (< $targetValue)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("Processamento de texto", e)
        }
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
            [$currentTime] Valor encontrado:
            Valor: $rawValue
            Alvo mínimo: $targetValue
            Node Info:
            ${dumpNodeHierarchy(node, maxDepth = 2)}
        """.trimIndent())

        // Aqui já sabemos que rawValue >= targetValue
        if (rawValue <= MyAccessibilityService.MAX_VALUE) {
            LogHelper.logEvent("Valor aceito: $rawValue")
            if (autoClickEnabled) {
                handleAutoClick(rawValue, node)
            } else {
                foundValues.add(rawValue)
                service.showNotification("Shopper: $$rawValue encontrado!")
            }
        } else {
            LogHelper.logEvent("Valor muito alto: $rawValue")
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
        LogHelper.logEvent("Monitor setEnabled chamado com: $enabled")
        this.enabled = enabled
        LogHelper.logEvent("==== MONITOR STATUS CHANGED ====")
        LogHelper.logEvent("Monitor ${if (enabled) "habilitado" else "desabilitado"}")
    }

    override fun setAutoClick(enabled: Boolean) {
        this.autoClickEnabled = enabled
        LogHelper.logEvent("Auto-click ${if (enabled) "habilitado" else "desabilitado"}")
    }

    override fun isAutoClickEnabled() = autoClickEnabled
}