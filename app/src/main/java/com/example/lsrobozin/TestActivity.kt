/*
 * TestActivity.kt
 * Current Date and Time (UTC): 2024-12-06 06:00:30
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

data class Batch(
    val price: Double,
    val items: Int,
    val units: Int,
    val distance: Double,
    val store1: String,
    val store2: String? = null
)

class TestActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val batches = mutableListOf<Batch>()
    private val batchAdapter = BatchAdapter(batches)
    private val handler = Handler(Looper.getMainLooper())

    private val stores = listOf(
        "Mariano's 802 Northwest Highway, Arlington Heights",
        "ALDI 1432 E. Rand Road, Prospect Heights",
        "Jewel-Osco 122 N Vail Ave, Arlington Heights",
        "Costco 999 N Milwaukee Ave, Vernon Hills",
        "Target 1700 E Rand Rd, Arlington Heights"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        recyclerView = findViewById(R.id.batch_list_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = batchAdapter

        // Simula novas batches a cada 5 segundos
        handler.postDelayed(object : Runnable {
            override fun run() {
                addNewBatch()
                handler.postDelayed(this, 5000)
            }
        }, 5000)

        // Adiciona primeira batch
        addNewBatch()
    }

    private fun addNewBatch() {
        val price = 15 + Random.nextDouble() * 35
        val items = Random.nextInt(10, 80)
        val units = items + Random.nextInt(0, 20)
        val distance = 1 + Random.nextDouble() * 9
        val store1 = stores.random()
        val store2 = if (Random.nextBoolean()) stores.random() else null

        val batch = Batch(
            price = price,
            items = items,
            units = units,
            distance = distance,
            store1 = store1,
            store2 = store2
        )

        batches.add(0, batch)
        if (batches.size > 5) {
            batches.removeAt(batches.size - 1)
        }
        batchAdapter.notifyDataSetChanged()
    }

    class BatchAdapter(private val batches: List<Batch>) :
        RecyclerView.Adapter<BatchAdapter.BatchViewHolder>() {

        class BatchViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
            RecyclerView.ViewHolder(inflater.inflate(R.layout.item_batch, parent, false)) {
            private val priceView = itemView.findViewById<TextView>(R.id.batch_price)
            private val detailsView = itemView.findViewById<TextView>(R.id.batch_details)
            private val distanceView = itemView.findViewById<TextView>(R.id.batch_distance)
            private val store1View = itemView.findViewById<TextView>(R.id.store1)
            private val store2View = itemView.findViewById<TextView>(R.id.store2)

            fun bind(batch: Batch) {
                priceView.text = "$%.2f".format(batch.price)
                detailsView.text = "${if (batch.store2 != null) "2" else "1"} shop and deliver • " +
                        "${batch.items} items (${batch.units} units)"
                distanceView.text = "%.1f mi".format(batch.distance)
                store1View.text = batch.store1
                store2View.text = batch.store2 ?: ""
                store2View.visibility = if (batch.store2 != null) android.view.View.VISIBLE
                else android.view.View.GONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatchViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return BatchViewHolder(inflater, parent)
        }

        override fun onBindViewHolder(holder: BatchViewHolder, position: Int) {
            holder.bind(batches[position])
        }

        override fun getItemCount() = batches.size
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}