package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

sealed class OrderListItem {
    data class Header (
        val providerName: String,
        val providerId: Int,
        val totalOrders: Int = 0,      // Sum of numeroOrdini
        val totalEarnings: Double = 0.0, // Sum of pagaTotale
        val hourlyRate: Double = 0.0,   // (Sum of pagaTotale * 60) / (Sum of tempoImpiegato)
        val totalTips: Double = 0.0,     // Sum of mancia + manciaContanti
        val totalContanti: Double = 0.0, // RiscossiContanti + ManciaContanti
        val totalManciaContanti: Double = 0.0, // ManciaContanti
        val totalTempo: Int = 0,
    ) : OrderListItem()

    data class OrderItem (
        val id: Long,
        val providerId: Int,
        val providerName: String
    ) : OrderListItem()
}

class OrderAdapter(
    private val databaseAdapter: DatabaseHelper
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ORDER = 1
    }
    private var items: List<OrderListItem> = emptyList()
    private var currentOrders: List<Order> = emptyList() // Cache ordini per refresh
    private var currentSqlClause: String = "" // Cache SQL clause
    private var onOrderSavedListener: (() -> Unit)? = null
    private var onOrderDeletedListener: (() -> Unit)? = null
    private var onHeaderClickListener: ((Int) -> Unit)? = null // Listener per notificare toggle

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is OrderListItem.Header -> VIEW_TYPE_HEADER
            is OrderListItem.OrderItem -> VIEW_TYPE_ORDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view, ::toggleProviderVisibility)
            }
            VIEW_TYPE_ORDER -> {
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
                OrderViewHolder(orderItemView)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is OrderListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is OrderListItem.OrderItem -> (holder as OrderViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateOrders(orders: List<Order>, sqlClause: String, visibilityMap: Map<Int, Boolean> = emptyMap()) {
        currentOrders = orders // Cache ordini
        currentSqlClause = sqlClause // Cache SQL clause
        items = buildSectionedList(orders, sqlClause, visibilityMap)
        Log.d("OrderAdapter", "Items generati: $items")
        notifyDataSetChanged()
    }

    private fun buildSectionedList(orders: List<Order>, sqlClause: String, visibilityMap: Map<Int, Boolean>): List<OrderListItem> {
        val result = mutableListOf<OrderListItem>()
        val summaries = databaseAdapter.getProviderSummaries(sqlClause)
        Log.d("OrderAdapter", "Summaries: $summaries")
        val groupedOrders = orders.groupBy { it.providerId }
        Log.d("OrderAdapter", "Ordini raggruppati: $groupedOrders")
        for (summary in summaries) {
            result.add(summary)
            // Includi ordini solo se visibilityMap è vuota o se il provider è visibile
            if (visibilityMap[summary.providerId] != false) {
                val providerOrders = groupedOrders[summary.providerId] ?: emptyList()
                result.addAll(providerOrders.map {
                    OrderListItem.OrderItem(it.id, it.providerId, it.providerName)
                })
            }
        }
        return result
    }

    private fun toggleProviderVisibility(providerId: Int) {
        // Notifica l'Activity del toggle, passando il providerId
        onHeaderClickListener?.invoke(providerId)

        // Calcola il range di item da aggiornare (per aggiornare l'icona di toggle)
        var startPosition = -1
        for (i in items.indices) {
            if (items[i] is OrderListItem.Header && (items[i] as OrderListItem.Header).providerId == providerId) {
                startPosition = i
                break
            }
        }
        if (startPosition >= 0) {
            notifyItemRangeChanged(startPosition, 1) // Aggiorna solo l'header per l'icona
        }
    }

    fun setOnOrderSavedListener(listener: () -> Unit) {
        onOrderSavedListener = listener
    }

    fun setOnOrderDeletedListener(listener: () -> Unit) {
        onOrderDeletedListener = listener
    }

    fun setOnHeaderClickListener(listener: (Int) -> Unit) {
        onHeaderClickListener = listener
    }

    class HeaderViewHolder(itemView: View, private val onHeaderClick: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val headerTextView: TextView = itemView.findViewById(R.id.header_text)
        private val statsContainer: LinearLayout = itemView.findViewById(R.id.stats_container)

        init {
            itemView.setOnClickListener {
                val header = itemView.tag as? OrderListItem.Header
                Log.d("OrderAdapter", "Header clicked, providerId: ${header?.providerId}")
                header?.let { onHeaderClick(it.providerId) }
            }
        }
        fun bind(header: OrderListItem.Header) {
            itemView.tag = header
            headerTextView.text = header.providerName
            statsContainer.removeAllViews()
            val context = itemView.context

            // Definisci i testi per le due righe
            val text1 = "${context.getString(R.string.ordini)}: # ${header.totalOrders}"
            val text2 = "${context.getString(R.string.totale)}: ${CurrencyFormatter.format(header.totalEarnings)} / ${CurrencyFormatter.format(header.totalContanti)}"
            val text3 = "${context.getString(R.string.oraria)}: ${CurrencyFormatter.format(header.hourlyRate)}/h"
            val text4 = "${context.getString(R.string.mancia_grid)}: ${CurrencyFormatter.format(header.totalTips)} / ${CurrencyFormatter.format(header.totalManciaContanti)}"

            // Prima riga: Ordini e Guadagno
            val linearLayout1 = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                weightSum = 7f
            }

            val textView1 = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    3f
                )
                text = text1
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START // Etichetta e valore insieme, allineato a sinistra
                setPadding(0, 0, 8, 2)
            }
            val textView2 = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    4f
                )
                text = text2
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END // Allineato a destra
                setPadding(0, 0, 0, 2)
                            }
            linearLayout1.addView(textView1)
            linearLayout1.addView(textView2)
            statsContainer.addView(linearLayout1)

            // Seconda riga: Paga Oraria e Mance
            val linearLayout2 = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                weightSum = 7f
            }

            val textView3 = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    3f
                )
                text = text3
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START // Etichetta e valore insieme, allineato a sinistra
                setPadding(0, 2, 8, 0)

            }
            val textView4 = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    4f
                )
                text = text4
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END // Allineato a destra
                setPadding(0, 2, 0, 0)
            }
            linearLayout2.addView(textView3)
            linearLayout2.addView(textView4)
            statsContainer.addView(linearLayout2)
        }
    }

    class OrderViewHolder(private val orderItemView: OrderItemView) : RecyclerView.ViewHolder(orderItemView) {
        fun bind(orderItem: OrderListItem.OrderItem) {
            orderItemView.setOrderData(orderItem.id)
        }
    }
}
