package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class OrderAdapter : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {
    private var orderIds: List<Long> = emptyList()
    private var onOrderSavedListener: (() -> Unit)? = null
    private var onOrderDeletedListener: (() -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val orderItemView = OrderItemView(parent.context)
        orderItemView.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        orderItemView.setOnOrderSavedListener {
            onOrderSavedListener?.invoke()
        }
        orderItemView.setOnOrderDeletedListener {
            onOrderDeletedListener?.invoke()
        }
        return OrderViewHolder(orderItemView)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orderIds[position])
    }

    override fun getItemCount(): Int = orderIds.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateOrders(newOrderIds: List<Long>) {
        orderIds = newOrderIds
        notifyDataSetChanged()
    }

    fun setOnOrderSavedListener(listener: () -> Unit) {
        onOrderSavedListener = listener
    }

    fun setOnOrderDeletedListener(listener: () -> Unit) {
        onOrderDeletedListener = listener
    }

    class OrderViewHolder(private val orderItemView: OrderItemView) : RecyclerView.ViewHolder(orderItemView) {
        fun bind(orderId: Long) {
            orderItemView.setOrderData(orderId)
        }
    }
}