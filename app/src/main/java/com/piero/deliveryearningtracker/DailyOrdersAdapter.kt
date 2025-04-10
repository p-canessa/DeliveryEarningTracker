package com.piero.deliveryearningtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale


class DailyOrdersAdapter(private val orders: List<DailyOrder>) :
    RecyclerView.Adapter<DailyOrdersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvNumberOrders: TextView = view.findViewById(R.id.tv_number_orders)
        val tvTotalGross: TextView = view.findViewById(R.id.tv_total_gross)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        holder.tvDate.text = order.date
        holder.tvNumberOrders.text =  String.format(Locale.getDefault(),"%d", order.numberOfOrders)
        holder.tvTotalGross.text = CurrencyFormatter.format(order.totalGross)
    }

    override fun getItemCount() = orders.size
}

// Classe di esempio per i dati
data class DailyOrder(val date: String, val numberOfOrders: Int, val totalGross: Double)