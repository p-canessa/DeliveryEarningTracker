package com.piero.deliveryearningtracker

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ErrorOrdersActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error_orders)

        dbHelper = DatabaseHelper(this)
        recyclerView = findViewById(R.id.orders_recycler_view)
        orderAdapter = OrderAdapter(dbHelper)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter

        val errorType = intent.getStringExtra("error_type")
        val date = intent.getStringExtra("date")
        val month = intent.getIntExtra("month", 1)
        val year = intent.getIntExtra("year", 2023)

        val sqlClause = when (errorType) {
            "compenso", "entrambi" -> "WHERE o.Data = '$date'"
            "mancia_minore" -> "WHERE strftime('%m', o.Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', o.Data) = '$year' AND (o.Mancia = o.ManciaContanti)"
            "mancia_maggiore" -> "WHERE strftime('%m', o.Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', o.Data) = '$year' AND (o.Mancia != o.ManciaContanti)"
            "contanti_maggiore" -> "WHERE strftime('%m', o.Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', o.Data) = '$year' AND o.RiscossiContanti > 0"
            "contanti_minore" -> "WHERE strftime('%m', o.Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', o.Data) = '$year' AND o.RiscossiContanti = 0"
            else -> ""
        }

        val orders = dbHelper.getOrders(sqlClause)
        Log.d("ErrorOrdersActivity", "Ordini trovati: $orders")
        orderAdapter.updateOrders(orders, sqlClause)

        // Aggiorna i totali nella RiconciliazioneActivity quando un ordine viene salvato o eliminato
        orderAdapter.setOnOrderSavedListener { finish() }
        orderAdapter.setOnOrderDeletedListener { finish() }
    }
}