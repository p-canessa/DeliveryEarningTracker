package com.piero.deliveryearningtracker

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubscriptionAdapter(private val billingManager: BillingManager) : RecyclerView.Adapter<SubscriptionAdapter.SubscriptionViewHolder>() {
    private var subscriptions: List<SubscriptionModel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subscription, parent, false)
        Log.d("SubscriptionAdapter", "itemView type: ${view.javaClass.simpleName}")
        return SubscriptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        holder.bind(subscriptions[position])
    }

    override fun getItemCount(): Int = subscriptions.size

    inner class SubscriptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subscriptionName: TextView = itemView.findViewById(R.id.subscription_name)
        private val basePlanName: TextView = itemView.findViewById(R.id.base_plan_name)
        private val offerContainer: LinearLayout = itemView.findViewById(R.id.offer_container)
        private val offerDescription: TextView = itemView.findViewById(R.id.offer_description)
        private val offerDetails: TextView = itemView.findViewById(R.id.offer_details)
        private val offerPrice: TextView = itemView.findViewById(R.id.offer_price)
        private val subscribeButton: Button = itemView.findViewById(R.id.subscribe_button)

        fun bind(subscription: SubscriptionModel) {
            // Resetta la visibilità
            subscriptionName.visibility = View.GONE
            basePlanName.visibility = View.GONE
            offerContainer.visibility = View.GONE
            subscribeButton.visibility = View.GONE

            // Intestazione
            subscriptionName.text = subscription.nameTranslated
            subscriptionName.visibility = View.VISIBLE
            Log.d("SubscriptionAdapter", "Binding SubscriptionHeader: ${subscription.nameTranslated}")

            // Piano base
            val basePlan = subscription.basePlans.firstOrNull()
            if (basePlan != null) {
                val displayTitle = basePlan.title.takeIf { it.isNotEmpty() }
                    ?: itemView.context.getString(R.string.base_plan_default)
                basePlanName.text = displayTitle
                basePlanName.visibility = View.VISIBLE
                Log.d("SubscriptionAdapter", "Binding BasePlan: $displayTitle")
            }

            // Offerta
            val offer = basePlan?.offers?.firstOrNull()
            if (offer != null) {
                offerContainer.visibility = View.VISIBLE
                offerDescription.text = offer.description
                val recurrence = offer.recurrence
                val price = formatPrice(offer.price, offer.pricingPhases.last().billingPeriod, subscription.productDetails.productId)
                offerPrice.text = price
                offerDetails.text = recurrence
                subscribeButton.visibility = View.VISIBLE
                subscribeButton.setOnClickListener {
                    billingManager.launchBillingFlow(itemView.context as Activity, subscription.productDetails, offer.offerId)
                    Log.d("SubscriptionAdapter", "Subscribe button clicked for offer: ${offer.offerId}")
                }
                Log.d("SubscriptionAdapter", "Binding Offer: ${offer.description}, Dettagli: $recurrence, $price")
            }
        }

        private fun formatPrice(price: String, billingPeriod: String, productId: String): String {
            return when {
                billingPeriod == "P1M" && productId == "annuale_rate" -> "$price/month"
                billingPeriod == "P1M" && productId == "remove_ads_monthly" -> "$price/month"
                billingPeriod == "P1Y" -> "$price/year"
                else -> price
            }
        }
    }

    fun submitSubscriptions(subscriptions: List<SubscriptionModel>) {
        this.subscriptions = subscriptions
        notifyDataSetChanged()
        Log.d("SubscriptionAdapter", "submitSubscriptions chiamato con ${subscriptions.size} sottoscrizioni")
    }
}