package com.piero.deliveryearningtracker

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*

class BillingManager(private val context: Context, private val onSubscriptionChanged: (Boolean) -> Unit) {
    private val billingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                        onSubscriptionChanged(true)
                    }
                }
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts() // Abilita supporto per prodotti one-time
                .build()
        )
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkSubscription()
                } else {
                    // Aggiungi un log per debug
                    Log.e(
                        "BillingManager",
                        "Errore connessione Billing: ${billingResult.debugMessage}"
                    )
                    Toast.makeText(
                        context,
                        "Errore connessione billing: ${billingResult.responseCode}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    fun launchBillingFlow(activity: Activity) {
        Log.d("BillingManager", "Avvio launchBillingFlow con activity: ${activity.javaClass.simpleName}")
        if (activity.isFinishing || activity.isDestroyed) {
            Log.e("BillingManager", "Activity non valida per il flusso di acquisto")
            Toast.makeText(context, "Errore: Activity non valida", Toast.LENGTH_LONG).show()
            return
        }
        Log.d("BillingManager", "Activity valida: ${!activity.isFinishing && !activity.isDestroyed}")

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("remove_ads_monthly")
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            Log.d("BillingManager", "Risultato query: ${billingResult.responseCode}, Messaggio: ${billingResult.debugMessage}, Prodotti trovati: ${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                Log.d("BillingManager", "Prodotto trovato: ${productDetails.productId}, Nome: ${productDetails.name}, Descrizione: ${productDetails.description}")

                // Verifica le offerte disponibili
                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
                Log.d("BillingManager", "Offerte disponibili: ${subscriptionOfferDetails?.size ?: 0}")
                if (subscriptionOfferDetails.isNullOrEmpty()) {
                    Log.e("BillingManager", "Nessuna offerta trovata per il prodotto")
                    Toast.makeText(context, "Errore: Nessuna offerta disponibile", Toast.LENGTH_LONG).show()
                    return@queryProductDetailsAsync
                }

                // Cerca l'offerta corrispondente al piano base "remove-ads-monthly"
                val targetOffer = subscriptionOfferDetails.find { offer ->
                    offer.basePlanId == "remove-ads-monthly"
                }
                if (targetOffer == null) {
                    Log.e("BillingManager", "Offerta per piano base 'remove-ads-monthly' non trovata")
                    Toast.makeText(context, "Errore: Piano base non trovato", Toast.LENGTH_LONG).show()
                    return@queryProductDetailsAsync
                }

                // Usa l'offerToken del piano base
                val offerToken = targetOffer.offerToken
                Log.d("BillingManager", "Offer token per 'remove-ads-monthly': $offerToken")

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    ))
                    .build()

                Log.d("BillingManager", "Avvio flusso di acquisto")
                val billingResult = billingClient.launchBillingFlow(activity, flowParams)
                Log.d("BillingManager", "Risultato launchBillingFlow: ${billingResult.responseCode}, Messaggio: ${billingResult.debugMessage}")
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Toast.makeText(context, "Errore avvio flusso: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("BillingManager", "Errore query prodotto: ${billingResult.debugMessage}, Prodotti: ${productDetailsList.size}")
                Toast.makeText(context, "Prodotto non trovato", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkSubscription() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(queryPurchasesParams) { _, purchases ->
            val isSubscribed = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            onSubscriptionChanged(isSubscribed)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                // Acquisto riconosciuto con successo
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Toast.makeText(context, "Acquisto riconosciuto con successo", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("BillingManager", "Errore riconoscimento: ${billingResult.debugMessage}")
                    Toast.makeText(context, "Errore riconoscimento acquisto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}