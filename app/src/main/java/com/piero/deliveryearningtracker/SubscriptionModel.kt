package com.piero.deliveryearningtracker

import com.android.billingclient.api.ProductDetails

data class SubscriptionModel(
    val productId: String,
    val nameJson: Any?, // JSON originale da Firestore
    val nameTranslated: String, // Valore tradotto
    val descriptionJson: Any?, // JSON originale da Firestore
    val descriptionTranslated: String, // Valore tradotto
    val recurrenceJson: Any?, // JSON originale da Firestore
    val recurrenceTranslated: String, // Valore tradotto
    val basePlans: List<BasePlanModel>,
    val productDetails: ProductDetails
)

data class BasePlanModel(
    val basePlanId: String,
    val title: String,
    val isAutoRenewing: Boolean,
    val offers: List<OfferModel>,
    val price: String
)

data class OfferModel(
    val offerId: String,
    val offerTags: List<String>,
    val description: String,
    val price: String,
    val pricingPhases: List<PricingPhase>,
    val recurrence: String?
)

data class PricingPhase(
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val recurrenceMode: Int,
    val billingCycleCount: Int?
)