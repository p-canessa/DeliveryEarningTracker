package com.piero.deliveryearningtracker

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*

class BillingManager(private val context: Context, private val onSubscriptionChanged: (Boolean) -> Unit) {
    private val billingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            Log.d("BillingManager", "PurchasesUpdated: responseCode=${billingResult.responseCode}, message=${billingResult.debugMessage}, purchases=${purchases?.size ?: 0}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    Log.d("BillingManager", "Acquisto rilevato: token=${purchase.purchaseToken}, state=${purchase.purchaseState}, products=${purchase.products}, isAcknowledged=${purchase.isAcknowledged}")
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }
                            onSubscriptionChanged(true)
                        }
                        Purchase.PurchaseState.PENDING -> {
                            Log.d("BillingManager", "Acquisto in sospeso: ${purchase.purchaseToken}")
                            Toast.makeText(context, "Acquisto in sospeso, attendi", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Log.d("BillingManager", "Acquisto non valido: state=${purchase.purchaseState}")
                        }
                    }
                }
            } else {
                Log.e("BillingManager", "Errore PurchasesUpdated: responseCode=${billingResult.responseCode}, message=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    Log.d("BillingManager", "Abbonamento già attivo, verifica stato")
                    checkSubscription()
                } else {
                    Toast.makeText(context, "Errore acquisto: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
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

                // Log dei dettagli dell'offerta
                Log.d("BillingManager", "Dettagli offerta: basePlanId=${targetOffer.basePlanId}, offerId=${targetOffer.offerId}, offerToken=${targetOffer.offerToken}")
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
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Flusso avviato, verifica abbonamento
                    checkSubscription()
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    Log.d("BillingManager", "Abbonamento già attivo, verifica stato")
                    Toast.makeText(context, "Abbonamento già attivo", Toast.LENGTH_SHORT).show()
                    checkSubscription()
                } else {
                    Log.e("BillingManager", "Errore avvio flusso: ${billingResult.debugMessage}")
                    Toast.makeText(context, "Errore avvio flusso: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("BillingManager", "Errore query prodotto: ${billingResult.debugMessage}, Prodotti: ${productDetailsList.size}")
                Toast.makeText(context, "Prodotto non trovato", Toast.LENGTH_SHORT).show()
                checkSubscription()
            }
        }
    }

    private fun checkSubscription() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            Log.d("BillingManager", "Query acquisti: responseCode=${billingResult.responseCode}, message=${billingResult.debugMessage}, acquisti trovati=${purchases.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var isSubscribed = false
                for (purchase in purchases) {
                    Log.d("BillingManager", "Acquisto trovato: token=${purchase.purchaseToken}, state=${purchase.purchaseState}, isAcknowledged=${purchase.isAcknowledged}, products=${purchase.products}")
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isSubscribed = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        Log.d("BillingManager", "Acquisto in sospeso: ${purchase.purchaseToken}")
                        Toast.makeText(context, "Acquisto in sospeso, attendi", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.d("BillingManager", "Stato abbonamento: $isSubscribed")
                onSubscriptionChanged(isSubscribed)
            } else {
                Log.e("BillingManager", "Errore query acquisti: ${billingResult.debugMessage}")
                Toast.makeText(context, "Errore verifica abbonamento", Toast.LENGTH_SHORT).show()
            }
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