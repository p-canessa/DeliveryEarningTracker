package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

private const val MAX_RECONNECT_ATTEMPTS = 5

class BillingManager private constructor(
    private val context: Context
) {
    private val listeners = mutableSetOf<(Boolean, String?) -> Unit>()
    private var billingClient: BillingClient? = null
    private var reconnectAttempts = 0
    private var isConnecting = false
    private var lastSubscribedState: Boolean? = null
    private val userLocale: String = Locale.getDefault().language // es. "it", "uk"
    private val fallbackLocale: String = "it" // Fallback all'italiano

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        initialize()
    }

    fun initialize() {
        Log.d("BillingManager", "Inizializzazione BillingManager")
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchasesUpdated(billingResult, purchases)
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Connessione BillingClient: ${billingResult.responseCode}")
                    reconnectAttempts = 0
                    isConnecting = false
                    checkSubscription()
                } else {
                    Log.e("BillingManager", "Errore connessione: ${billingResult.debugMessage}")
                    retryConnection()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "BillingClient disconnesso")
                retryConnection()
            }
        })
    }

    private fun retryConnection() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && !isConnecting) {
            isConnecting = true
            reconnectAttempts++
            Log.d("BillingManager", "Tentativo di riconnessione ($reconnectAttempts)")
            Handler(Looper.getMainLooper()).postDelayed({
                billingClient?.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d("BillingManager", "Riconnessione riuscita")
                            reconnectAttempts = 0
                            isConnecting = false
                            checkSubscription()
                        } else {
                            Log.e("BillingManager", "Errore riconnessione: ${billingResult.debugMessage}")
                            retryConnection()
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.w("BillingManager", "BillingClient disconnesso durante riconnessione")
                        retryConnection()
                    }
                })
            }, 2000L * reconnectAttempts)
        } else {
            Log.e("BillingManager", "Numero massimo di tentativi di riconnessione raggiunto")
            isConnecting = false
        }
    }

    fun addSubscriptionListener(listener: (Boolean, String?) -> Unit) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
                Log.d("BillingManager", "Listener aggiunto: $listener")
                if (billingClient?.isReady == true) {
                    checkSubscription()
                }
            }
        }
    }

    fun removeSubscriptionListener(listener: (Boolean, String?) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
            Log.d("BillingManager", "Listener rimosso: $listener")
        }
    }

    private suspend fun isNewSubscriber(): Boolean = withContext(Dispatchers.IO) {
        if (billingClient == null) {
            Log.w("BillingManager", "BillingClient è null, assumo nuovo abbonato")
            return@withContext true
        }
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val result = billingClient?.queryPurchasesAsync(params)
            val purchases = result?.purchasesList ?: return@withContext true
            purchases.none { purchase ->
                purchase.products.contains("annuale_standard") && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        } catch (e: Exception) {
            Log.e("BillingManager", "Errore in queryPurchasesAsync: $e")
            return@withContext true
        }
    }

    private fun formatBillingPeriod(context: Context, billingPeriod: String, productId: String): String {
        return when {
            billingPeriod == "P1M" && productId == "annuale_rate" -> context.getString(R.string.recurrence_12_monthly_payments)
            billingPeriod == "P1M" && productId == "remove_ads_monthly" -> context.getString(R.string.recurrence_monthly)
            billingPeriod == "P1Y" -> context.getString(R.string.recurrence_annual)
            else -> {
                val regex = Regex("""P(?:(\d+)W)?(?:(\d+)D)?""")
                val match = regex.matchEntire(billingPeriod) ?: return billingPeriod
                val weeks = match.groups[1]?.value?.toIntOrNull() ?: 0
                val days = match.groups[2]?.value?.toIntOrNull() ?: 0
                val totalDays = weeks * 7 + days
                Log.d("BillingManager", "Formattazione billingPeriod: $billingPeriod -> totalDays=$totalDays")
                when (totalDays) {
                    1 -> context.getString(R.string.one_day)
                    7 -> context.getString(R.string.one_week)
                    14 -> context.resources.getQuantityString(R.plurals.weeks, 2, 2)
                    21 -> context.resources.getQuantityString(R.plurals.weeks, 3, 3)
                    15 -> context.resources.getQuantityString(R.plurals.days, 15, 15)
                    30, 31 -> context.getString(R.string.one_month)
                    60 -> context.resources.getQuantityString(R.plurals.months, 2, 2)
                    90 -> context.resources.getQuantityString(R.plurals.months, 3, 3)
                    120 -> context.resources.getQuantityString(R.plurals.months, 4, 4)
                    180 -> context.resources.getQuantityString(R.plurals.months, 6, 6)
                    365 -> context.resources.getQuantityString(R.plurals.months, 12, 12)
                    else -> context.resources.getQuantityString(R.plurals.days, totalDays, totalDays)
                }
            }
        }
    }

    private fun parseToListOfMaps(data: Any?, fieldName: String): List<Map<String, Any>> {
        if (data == null) {
            Log.w("BillingManager", "$fieldName: Dati null, restituisco lista vuota")
            return emptyList()
        }

        // Caso 1: Lista di mappe (formato atteso)
        if (data is List<*>) {
            val result = data.filterIsInstance<Map<String, Any>>()
            if (result.size != data.size) {
                Log.w("BillingManager", "$fieldName: Alcuni elementi non sono mappe valide: $data")
            }
            return result
        }

        // Caso 2: Stringa JSON
        if (data is String) {
            try {
                // Prova a parsare come JSONArray
                val jsonArray = JSONArray(data)
                val result = mutableListOf<Map<String, Any>>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    result.add(jsonObject.toMap())
                }
                Log.d("BillingManager", "$fieldName: Parsato JSON in lista di mappe: $result")
                return result
            } catch (e: JSONException) {
                Log.w("BillingManager", "$fieldName: Non è un JSON valido: $data, errore: ${e.message}")
            }
        }

        // Caso 3: Stringa CSV
        if (data is String && data.contains(";")) {
            try {
                val lines = data.split("\n").filter { it.isNotBlank() }
                val result = lines.mapNotNull { line ->
                    val pairs = line.split(";").map { it.split(",") }
                    if (pairs.all { it.size == 2 }) {
                        pairs.associate { it[0].trim() to it[1].trim() as Any }
                    } else {
                        null
                    }
                }
                Log.d("BillingManager", "$fieldName: Parsato CSV in lista di mappe: $result")
                return result
            } catch (e: Exception) {
                Log.w("BillingManager", "$fieldName: Errore parsing CSV: $data, errore: ${e.message}")
            }
        }

        // Caso di fallback: formato non riconosciuto
        Log.w("BillingManager", "$fieldName: Formato non riconosciuto: $data")
        return emptyList()
    }
    suspend fun querySubscriptions(callback: (List<SubscriptionModel>?, String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val isNewSubscriber = isNewSubscriber()
        db.collection("subscriptions")
            .get()
            .addOnSuccessListener { result ->
                val productIds = result.documents.mapNotNull { it.getString("productID") }.distinct()
                Log.d("BillingManager", "Product IDs recuperati da Firestore: $productIds")

                if (productIds.isEmpty()) {
                    Log.w("BillingManager", "Nessun product ID trovato")
                    callback(null, "Nessun product ID trovato")
                    return@addOnSuccessListener
                }

                val productList = productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.e("BillingManager", "Errore Play Billing: ${billingResult.debugMessage}")
                        callback(null, "Errore Play Billing: ${billingResult.debugMessage}")
                        return@queryProductDetailsAsync
                    }

                    val subscriptions = productDetailsList.mapNotNull { productDetails ->
                        val productId = productDetails.productId
                        val document = result.documents.firstOrNull { it.getString("productID") == productId }

                        // Estrai i campi JSON e tradotti da Firestore
                        val nameJson = document?.get("name")
                        val nameTranslated = getTranslatedField(nameJson, "name", productDetails)
                        val recurrenceJson = document?.get("recurrence")
                        val recurrenceTranslated = getTranslatedField(recurrenceJson, "recurrence", productDetails)
                        val descriptionJson = document?.get("description")
                        val descriptionTranslated = getTranslatedField(descriptionJson, "description", productDetails)

                        // Recupera basePlans da Firestore
                        val basePlansFromFirestore = parseToListOfMaps(document?.get("basePlans"), "basePlans")
                        val basePlanMap = mutableMapOf<String, MutableList<OfferModel>>()

                        // Processa le offerte da Google Play Billing
                        productDetails.subscriptionOfferDetails?.forEach { offerDetails ->
                            val basePlanId = offerDetails.basePlanId
                            val offerId = offerDetails.offerToken

                            // Determina se l'offerta ha una prova gratuita
                            val hasFreeTrial = offerDetails.pricingPhases.pricingPhaseList.any { phase ->
                                phase.priceAmountMicros == 0L && phase.billingPeriod.isNotEmpty()
                            }
                            val isFreeTrialOffer = hasFreeTrial || offerDetails.offerTags.contains("free_trial")

                            // Filtra le offerte per annuale_standard
                            if (productId == "annuale_standard" && basePlanId == "annuale-standard") {
                                if (isNewSubscriber && !isFreeTrialOffer) return@forEach
                                if (!isNewSubscriber && isFreeTrialOffer) return@forEach
                            }

                            // Recupera dati da Firestore per l'offerta, se presenti
                            val firestoreOffers = parseToListOfMaps(
                                basePlansFromFirestore.find { it["basePlanId"] == basePlanId }?.get("offers"),
                                "offers"
                            )
                            val offerData = firestoreOffers.find { it["offerId"] == offerId }

                            // Determina il template di descrizione
                            val descriptionTemplate = offerData?.get("descriptionTemplate")?.let {
                                getTranslatedField(it, "descriptionTemplate", productDetails)
                            } ?: if (isFreeTrialOffer) context.getString(R.string.offer_free_trial_template)
                            else context.getString(R.string.offer_standard_template)

                            // Calcola la durata della prova gratuita
                            val freeTrialDuration = if (isFreeTrialOffer) {
                                val freeTrialPhase = offerDetails.pricingPhases.pricingPhaseList.find { phase ->
                                    phase.priceAmountMicros == 0L && phase.billingPeriod.isNotEmpty()
                                }
                                freeTrialPhase?.billingPeriod?.let { formatBillingPeriod(context, it, productId) } ?: ""
                            } else {
                                ""
                            }

                            // Recupera il prezzo formattato
                            val price = offerDetails.pricingPhases.pricingPhaseList.last().formattedPrice
                            val billingPeriod = offerDetails.pricingPhases.pricingPhaseList.last().billingPeriod
                            val formattedBillingPeriod = formatBillingPeriod(context, billingPeriod, productId)

                            // Formatta la descrizione
                            val description = if (isFreeTrialOffer) {
                                descriptionTemplate.format(freeTrialDuration, price)
                            } else {
                                when (productId) {
                                    "annuale_rate" -> context.getString(R.string.offer_12_monthly_payments_template, price)
                                    "remove_ads_monthly" -> context.getString(R.string.offer_monthly_template, price)
                                    else -> descriptionTemplate.format(price)
                                }
                            }

                            val offer = OfferModel(
                                offerId = offerId,
                                offerTags = offerDetails.offerTags,
                                description = description,
                                price = price,
                                pricingPhases = offerDetails.pricingPhases.pricingPhaseList.map { phase ->
                                    PricingPhase(
                                        priceAmountMicros = phase.priceAmountMicros,
                                        priceCurrencyCode = phase.priceCurrencyCode,
                                        formattedPrice = phase.formattedPrice,
                                        billingPeriod = phase.billingPeriod,
                                        recurrenceMode = phase.recurrenceMode,
                                        billingCycleCount = phase.billingCycleCount
                                    )
                                },
                                recurrence = when (productId) {
                                    "annuale_rate" -> context.getString(R.string.recurrence_12_monthly_payments)
                                    "remove_ads_monthly" -> context.getString(R.string.recurrence_monthly)
                                    "annuale_standard" -> if (isFreeTrialOffer) context.getString(R.string.recurrence_free_trial_annual)
                                    else context.getString(R.string.recurrence_annual)
                                    else -> formattedBillingPeriod
                                }
                            )
                            basePlanMap.getOrPut(basePlanId) { mutableListOf() }.add(offer)
                        }

                        // Crea i BasePlanModel
                        val basePlans = basePlanMap.map { (basePlanId, offers) ->
                            val selectedOffers = if (basePlanId == "annuale-standard") {
                                offers.take(1) // Prendi solo la prima offerta valida
                            } else {
                                offers
                            }
                            val firestoreBasePlan = basePlansFromFirestore.find { it["basePlanId"] == basePlanId }
                            val title = firestoreBasePlan?.get("title") as? String ?: when (basePlanId) {
                                "annuale-rateizato" -> context.getString(R.string.base_plan_annual_installments)
                                "annuale-standard" -> context.getString(R.string.base_plan_annual_standard)
                                "remove-ads-monthly" -> context.getString(R.string.base_plan_monthly)
                                else -> context.getString(R.string.base_plan_default)
                            }
                            BasePlanModel(
                                basePlanId = basePlanId,
                                title = title,
                                isAutoRenewing = true,
                                offers = selectedOffers,
                                price = selectedOffers.firstOrNull()?.price ?: ""
                            )
                        }

                        // Restituisci SubscriptionModel solo se ci sono base plans
                        if (basePlans.isNotEmpty()) {
                            SubscriptionModel(
                                productId = productId,
                                nameJson = nameJson,
                                nameTranslated = nameTranslated,
                                descriptionJson = descriptionJson,
                                descriptionTranslated = descriptionTranslated,
                                recurrenceJson = recurrenceJson,
                                recurrenceTranslated = recurrenceTranslated,
                                basePlans = basePlans,
                                productDetails = productDetails
                            )
                        } else {
                            Log.w("BillingManager", "Nessun base plan valido per productId: $productId")
                            null
                        }
                    }
                    Log.d("BillingManager", "Chiamata callback con ${subscriptions.size} sottoscrizioni")
                    callback(subscriptions, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("BillingManager", "Errore recupero product IDs: ${e.message}")
                callback(null, e.message)
            }
    }

    private fun getTranslatedField(field: Any?, fieldName: String, productDetails: ProductDetails?): String {
        Log.d("BillingManager", "Tentativo di estrarre campo $fieldName, valore: $field")
        when (field) {
            is String -> {
                try {
                    val jsonObject = JSONObject(field)
                    val map = jsonObject.toMap()
                    val translated = map[userLocale] as? String
                    if (translated != null) {
                        Log.d("BillingManager", "Campo $fieldName trovato come stringa JSON tradotta: $translated")
                        return translated
                    }
                    val fallback = map[fallbackLocale] as? String
                    if (fallback != null) {
                        Log.d("BillingManager", "Campo $fieldName trovato come stringa JSON di fallback: $fallback")
                        return fallback
                    }
                    val anyValue = map.values.firstOrNull { it is String } as? String
                    if (anyValue != null) {
                        Log.d("BillingManager", "Campo $fieldName trovato come primo valore disponibile in JSON: $anyValue")
                        return anyValue
                    }
                    Log.w("BillingManager", "Campo $fieldName non contiene traduzioni valide in JSON, usato come stringa: $field")
                    return field
                } catch (e: JSONException) {
                    Log.d("BillingManager", "Campo $fieldName non è un JSON valido: $field, errore: ${e.message}")
                    return field
                }
            }
            is Map<*, *> -> {
                val translated = field[userLocale] as? String
                if (translated != null) {
                    Log.d("BillingManager", "Campo $fieldName trovato come mappa tradotta: $translated")
                    return translated
                }
                val fallback = field[fallbackLocale] as? String
                if (fallback != null) {
                    Log.d("BillingManager", "Campo $fieldName trovato come mappa di fallback: $fallback")
                    return fallback
                }
                val anyValue = field.values.firstOrNull { it is String } as? String
                if (anyValue != null) {
                    Log.d("BillingManager", "Campo $fieldName trovato come primo valore disponibile in mappa: $anyValue")
                    return anyValue
                }
                Log.w("BillingManager", "Campo $fieldName non contiene traduzioni valide in mappa")
            }
        }
        Log.w("BillingManager", "Campo $fieldName non valido o non trovato, uso fallback")
        return when (fieldName) {
            "name" -> productDetails?.name ?: "Sconosciuto"
            "description" -> context.getString(R.string.description_unavailable)
            "descriptionTemplate" -> context.getString(R.string.offer_standard_template)
            "recurrence" -> context.getString(R.string.recurrence_unknown)
            else -> ""
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerId: String?) {
        if (billingClient == null || !billingClient!!.isReady) {
            Log.w("BillingManager", "BillingClient non pronto")
            return
        }
        val offerToken = offerId?.let { id ->
            productDetails.subscriptionOfferDetails?.find { it.offerId == id }?.offerToken
        } ?: productDetails.subscriptionOfferDetails?.first()?.offerToken
        if (offerToken == null) {
            Log.e("BillingManager", "Nessun offerToken trovato")
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        billingClient?.launchBillingFlow(activity, billingFlowParams)?.let { billingResult ->
            Log.d("BillingManager", "Risultato avvio BillingFlow: ${billingResult.responseCode}, ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d("BillingManager", "PurchasesUpdated: responseCode=${billingResult.responseCode}, message=${billingResult.debugMessage}, purchases=${purchases?.size ?: 0}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                Log.d("BillingManager", "Acquisto rilevato: token=${purchase.purchaseToken}, state=${purchase.purchaseState}, products=${purchase.products}, isAcknowledged=${purchase.isAcknowledged}")
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
            checkSubscription()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "Acquisto annullato dall'utente")
            notifyListeners(lastSubscribedState ?: false, "Acquisto annullato")
        } else {
            Log.e("BillingManager", "Errore acquisto: ${billingResult.debugMessage}")
            notifyListeners(lastSubscribedState ?: false, "Errore acquisto")
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
                    checkSubscription()
                    notifyListeners(lastSubscribedState ?: false, "Acquisto riconosciuto")
                } else {
                    Log.e("BillingManager", "Errore riconoscimento acquisto: ${billingResult.debugMessage}")
                    notifyListeners(lastSubscribedState ?: false, "Errore riconoscimento acquisto")
                }
            }
        } else {
            Log.d("BillingManager", "Acquisto già riconosciuto")
            checkSubscription()
        }
    }

    fun checkSubscription() {
        if (billingClient == null || !billingClient!!.isReady) {
            Log.w("BillingManager", "BillingClient non pronto, attesa connessione")
            return
        }
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient?.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            Log.d("BillingManager", "Query acquisti: responseCode=${billingResult.responseCode}, message=${billingResult.debugMessage}, acquisti trovati=${purchases.size}")
            var isSubscribed = false
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    Log.d("BillingManager", "Acquisto trovato: token=${purchase.purchaseToken}, state=${purchase.purchaseState}, isAcknowledged=${purchase.isAcknowledged}, products=${purchase.products}")
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isSubscribed = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        Log.d("BillingManager", "Acquisto in sospeso: ${purchase.purchaseToken}")
                        notifyListeners(isSubscribed, "Acquisto in sospeso")
                    }
                }
            } else {
                Log.e("BillingManager", "Errore query acquisti: ${billingResult.debugMessage}")
                notifyListeners(false, "Errore verifica abbonamento")
            }
            Log.d("BillingManager", "Stato abbonamento Play Store: $isSubscribed")
            if (isSubscribed != lastSubscribedState) {
                lastSubscribedState = isSubscribed
                updateAdsEnabledState(isSubscribed)
                notifyListeners(isSubscribed, null)
            }
        }
    }

    private fun updateAdsEnabledState(isSubscribed: Boolean) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdsEnabled = !isSubscribed
        with(sharedPref.edit()) {
            putBoolean("ads_enabled", isAdsEnabled)
            apply()
        }
        Log.d("BillingManager", "Aggiornato ads_enabled in SharedPreferences: $isAdsEnabled")
    }

    private fun notifyListeners(isSubscribed: Boolean, message: String? = null) {
        val isAdsEnabled = !isSubscribed
        Log.d("BillingManager", "Stato finale: isSubscribed=$isSubscribed, ads_enabled=$isAdsEnabled, message=$message")
        synchronized(listeners) {
            listeners.forEach {
                Log.d("BillingManager", "Notifica listener: $it, isSubscribed=$isSubscribed")
                Handler(Looper.getMainLooper()).post {
                    it(isSubscribed, message)
                }
            }
        }
    }

    fun cleanup() {
        billingClient?.endConnection()
        billingClient = null
        INSTANCE = null
        Log.d("BillingManager", "Pulizia BillingManager completata")
    }

        private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = get(key)
        }
        return map
    }
}