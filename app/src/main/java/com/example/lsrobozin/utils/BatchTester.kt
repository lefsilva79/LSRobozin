/*
 * BatchTester.kt
 * Current Date and Time (UTC): 2024-12-06 05:40:58
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import kotlin.random.Random

class BatchTester(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var targetView: View? = null

    companion object {
        private val PRICE_RANGES = listOf(
            15.0..25.0,  // Faixa baixa
            26.0..35.0,  // Faixa média
            36.0..50.0   // Faixa alta
        )
    }

    fun start(view: View) {
        targetView = view
        isRunning = true
        generateBatch()
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun generateBatch() {
        if (!isRunning) return

        // Gera um preço aleatório
        val priceRange = PRICE_RANGES.random()
        val price = priceRange.start + Random.nextDouble() * (priceRange.endInclusive - priceRange.start)

        // Atualiza o texto na view
        targetView?.let {
            if (it is TextView) {
                it.text = "$%.2f".format(price)
            }
        }

        // Agenda próxima atualização
        handler.postDelayed({
            generateBatch()
        }, 3000) // Atualiza a cada 3 segundos
    }

    // Permite configurar preços personalizados
    fun setCustomPrice(price: Double) {
        targetView?.let {
            if (it is TextView) {
                it.text = "$%.2f".format(price)
            }
        }
    }
}