/*
 * TestBatchActivity.kt
 * Current Date and Time (UTC): 2024-12-06 05:47:18
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.lsrobozin.utils.BatchTester

class TestBatchActivity : AppCompatActivity() {
    private lateinit var batchTester: BatchTester
    private lateinit var priceTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cria TextView para teste
        priceTextView = TextView(this).apply {
            textSize = 24f
            setPadding(16, 16, 16, 16)
        }
        setContentView(priceTextView)

        // Inicializa o BatchTester
        batchTester = BatchTester(this)

        // Inicia a geração de preços
        batchTester.start(priceTextView)
    }

    override fun onDestroy() {
        super.onDestroy()
        batchTester.stop()
    }
}