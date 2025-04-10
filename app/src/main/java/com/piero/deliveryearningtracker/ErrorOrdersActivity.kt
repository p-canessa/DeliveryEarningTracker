package com.piero.deliveryearningtracker

import android.os.Bundle
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
        orderAdapter = OrderAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter

        val errorType = intent.getStringExtra("error_type")
        val date = intent.getStringExtra("date")
        val month = intent.getIntExtra("month", 1)
        val year = intent.getIntExtra("year", 2023)

        val sqlClause = when (errorType) {
            "compenso", "entrambi" -> "WHERE Data = '$date'"
            "mancia_minore" -> "WHERE strftime('%m', Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', Data) = '$year' AND (Mancia = ManciaContanti)"
            "mancia_maggiore" -> "WHERE strftime('%m', Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', Data) = '$year' AND (Mancia != ManciaContanti)"
            "contanti_maggiore" -> "WHERE strftime('%m', Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', Data) = '$year' AND RiscossiContanti > 0"
            "contanti_minore" -> "WHERE strftime('%m', Data) = '${month.toString().padStart(2, '0')}' AND strftime('%Y', Data) = '$year' AND RiscossiContanti = 0"
            else -> ""
        }

        val orderIds = dbHelper.getOrderIds(sqlClause)
        orderAdapter.updateOrders(orderIds)

        // Aggiorna i totali nella RiconciliazioneActivity quando un ordine viene salvato o eliminato
        orderAdapter.setOnOrderSavedListener { finish() }
        orderAdapter.setOnOrderDeletedListener { finish() }
    }
}