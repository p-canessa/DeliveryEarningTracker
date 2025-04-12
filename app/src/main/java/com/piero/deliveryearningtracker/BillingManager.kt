package com.piero.deliveryearningtracker

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.android.billingclient.api.*

class BillingManager private constructor(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    private val listeners = mutableSetOf<(Boolean) -> Unit>()
    private var billingClient: BillingClient? = null

    companion object {
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context, dbHelper: DatabaseHelper): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext, dbHelper).also {
                    instance = it
                    it.initialize()
                }
            }
        }
    }

    private fun initialize() {
        Log.d("BillingManager", "Inizializzazione BillingManager")
        billingClient = BillingClient.newBuilder(context)
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
                                dbHelper.insertSubscription(30)
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

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d("BillingManager", "Connessione BillingClient: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkSubscription()
                } else {
                    Toast.makeText(context, "Errore connessione billing: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "BillingClient disconnesso, tentativo di riconnessione")
                billingClient?.startConnection(this)
            }
        })
    }

    fun addSubscriptionListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        checkSubscription()
    }

    fun removeSubscriptionListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun launchBillingFlow(activity: Activity, productId: String = "remove_ads_monthly") {
        Log.d("BillingManager", "Avvio launchBillingFlow con activity: ${activity.javaClass.simpleName}, productId: $productId")
        if (activity.isFinishing || activity.isDestroyed) {
            Log.e("BillingManager", "Activity non valida per il flusso di acquisto")
            Toast.makeText(context, "Errore: Activity non valida", Toast.LENGTH_LONG).show()
            return
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            Log.d("BillingManager", "Risultato query: ${billingResult.responseCode}, Messaggio: ${billingResult.debugMessage}, Prodotti trovati: ${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                Log.d("BillingManager", "Prodotto trovato: ${productDetails.productId}, Nome: ${productDetails.name}, Descrizione: ${productDetails.description}")

                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
                Log.d("BillingManager", "Offerte disponibili: ${subscriptionOfferDetails?.size ?: 0}")
                if (subscriptionOfferDetails.isNullOrEmpty()) {
                    Log.e("BillingManager", "Nessuna offerta trovata per il prodotto")
                    Toast.makeText(context, "Errore: Nessuna offerta disponibile", Toast.LENGTH_LONG).show()
                    return@queryProductDetailsAsync
                }

                val targetOffer = subscriptionOfferDetails.find { offer ->
                    offer.basePlanId == productId
                }
                if (targetOffer == null) {
                    Log.e("BillingManager", "Offerta per piano base '$productId' non trovata")
                    Toast.makeText(context, "Errore: Piano base non trovato", Toast.LENGTH_LONG).show()
                    return@queryProductDetailsAsync
                }

                Log.d("BillingManager", "Dettagli offerta: basePlanId=${targetOffer.basePlanId}, offerId=${targetOffer.offerId}, offerToken=${targetOffer.offerToken}")
                val offerToken = targetOffer.offerToken
                Log.d("BillingManager", "Offer token per '$productId': $offerToken")

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    ))
                    .build()

                Log.d("BillingManager", "Avvio flusso di acquisto")
                billingClient?.launchBillingFlow(activity, flowParams)?.let { billingResult ->
                    Log.d("BillingManager", "Risultato launchBillingFlow: ${billingResult.responseCode}, Messaggio: ${billingResult.debugMessage}")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        checkSubscription()
                    } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        dbHelper.insertSubscription(30)
                    } else {
                        Log.e("BillingManager", "Errore avvio flusso: ${billingResult.debugMessage}")
                        Toast.makeText(context, "Errore avvio flusso: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                    }
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

        billingClient?.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
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
                Log.d("BillingManager", "Stato abbonamento Play Store: $isSubscribed")
                if (isSubscribed) {
                    dbHelper.insertSubscription(30)
                }
                updateSubscriptionState()
            } else {
                Log.e("BillingManager", "Errore query acquisti: ${billingResult.debugMessage}")
                Toast.makeText(context, "Errore verifica abbonamento", Toast.LENGTH_SHORT).show()
                updateSubscriptionState()
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        Log.d("BillingManager", "Avvio riconoscimento acquisto: token=${purchase.purchaseToken}")
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Acquisto riconosciuto con successo")
                    Toast.makeText(context, "Acquisto riconosciuto con successo", Toast.LENGTH_SHORT).show()
                    dbHelper.insertSubscription(30)
                } else {
                    Log.e("BillingManager", "Errore riconoscimento acquisto: ${billingResult.debugMessage}")
                    Toast.makeText(context, "Errore riconoscimento acquisto", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d("BillingManager", "Acquisto già riconosciuto: token=${purchase.purchaseToken}")
            dbHelper.insertSubscription(30)
        }
    }

    private fun updateSubscriptionState() {
        dbHelper.updateAdsEnabledState()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdsEnabled = sharedPref.getBoolean("ads_enabled", true)
        val isSubscribed = !isAdsEnabled
        Log.d("BillingManager", "Stato finale: isSubscribed=$isSubscribed, ads_enabled=$isAdsEnabled")
        listeners.forEach { it(isSubscribed) }
    }

    fun isSubscribed(): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return !sharedPref.getBoolean("ads_enabled", true)
    }

    fun cleanup() {
        billingClient?.endConnection()
        instance = null
    }
}