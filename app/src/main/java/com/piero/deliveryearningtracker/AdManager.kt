package com.piero.deliveryearningtracker

import android.app.Activity
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.AdRequest
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem

object AdManager {
    private var ocrRewardedAd: RewardedAd? = null
    private var statinoRewardedAd: RewardedAd? = null
    private const val OCR_AD_UNIT_ID = BuildConfig.AD_UNIT_ID_PREMIO1
    private const val STATINO_AD_UNIT_ID = BuildConfig.AD_UNIT_ID_PREMIO2
    private var isOcrAdLoaded = false
    private var isStatinoAdLoaded = false

    // Funzione per creare e aggiungere un banner AdView a un contenitore
    fun setupBannerAd(context: Context, container: ViewGroup, isAdsEnabled: Boolean): AdView? {
        if (!isAdsEnabled) {
            return null // Non creare l'AdView se gli annunci sono disabilitati
        }

        // Crea l'AdView programmaticamente
        val adView = AdView(context)
        adView.setAdSize(AdSize.BANNER) // Imposta la dimensione del banner
        adView.adUnitId = BuildConfig.AD_UNIT_ID_BANNER // Imposta l'ID da BuildConfig

        // Configura il layout per centrare il banner (opzionale)
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        adView.layoutParams = layoutParams

        // Aggiungi l'AdView al contenitore
        container.removeAllViews()
        container.addView(adView)

        // Carica l'annuncio
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        return adView // Ritorna l'AdView per eventuali riferimenti futuri
    }

    fun loadOcrAd(context: Context) {
        Log.d("AdManager", "Caricamento OCR ad")
        RewardedAd.load(context, OCR_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                ocrRewardedAd = ad
                isOcrAdLoaded = true
                Log.d("AdManager", "OCR Ad caricato")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                ocrRewardedAd = null
                isOcrAdLoaded = false
                Log.e("AdManager", "Errore caricamento OCR Ad: ${error.message}")
            }
        })
    }

    fun loadStatinoAd(context: Context) {
        Log.d("AdManager", "Caricamento Statino ad")
        RewardedAd.load(context, STATINO_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                statinoRewardedAd = ad
                isStatinoAdLoaded = true
                Log.d("AdManager", "Statino Ad caricato")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                statinoRewardedAd = null
                isStatinoAdLoaded = false
                Log.e("AdManager", "Errore caricamento Statino Ad: ${error.message}")
            }
        })
    }

    fun showOcrAd(activity: Activity, onRewardEarned: (RewardItem) -> Unit, onAdClosed: () -> Unit) {
        ocrRewardedAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    onAdClosed() // Chiamato quando l’utente chiude l’annuncio
                    isOcrAdLoaded = false
                    loadOcrAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AdManager", "Errore nella visualizzazione dell’annuncio: ${adError.message}")
                }
            }
            ad.show(activity) { rewardItem ->
                onRewardEarned(rewardItem) // Chiamato quando l’utente guadagna il premio
                loadOcrAd(activity) // Ricarica l’annuncio per il prossimo utilizzo
            }
        } ?: run {
            Log.d("AdManager", "OCR Ad non pronto")
            //Toast.makeText(activity, "Annuncio non pronto, riprova.", Toast.LENGTH_SHORT).show()
        }
    }

    fun showStatinoAd(activity: Activity, onRewardEarned: () -> Unit, onAdClosed: () -> Unit) {
        statinoRewardedAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    onAdClosed() // Chiamato quando l’utente chiude l’annuncio
                    isStatinoAdLoaded = false
                    loadStatinoAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AdManager", "Errore nella visualizzazione dell’annuncio: ${adError.message}")
                }
            }
            ad.show(activity) { _ ->
                onRewardEarned()
                loadStatinoAd(activity)
            }
        } ?: run {
            Log.d("AdManager", "Statino Ad non pronto")
        }
    }

    fun isOcrAdLoaded(): Boolean {
        return ocrRewardedAd != null && isOcrAdLoaded
    }

    fun isStatinoAdLoaded(): Boolean {
        return statinoRewardedAd != null && isStatinoAdLoaded
    }
    
    fun destroyBannerAd(adView: AdView?) {
        adView?.let { view ->
            try {
                // Rimuovi dal layout se ancora collegato
                (view.parent as? ViewGroup)?.removeView(view)
                // Distruggi il banner
                view.destroy()
                Log.d("AdManager", "Banner distrutto con successo: $view")
            } catch (e: Exception) {
                Log.e("AdManager", "Errore durante la distruzione del banner: ${e.message}")
            }
        } ?: Log.d("AdManager", "Nessun banner da distruggere: adView è null")
    }

    fun updateAds(
        context: Context,
        adContainer: ViewGroup?,
        adView: AdView?,
        dbHelper: DatabaseHelper,
        showBanner: Boolean = true
    ): AdView? {
        val isAdsEnabled = DisableAds.loadAdsEnabledState(context, dbHelper)
        Log.d("AdManager", "updateAds: isAdsEnabled=$isAdsEnabled, showBanner=$showBanner")

        var currentAdView = adView
        if (isAdsEnabled && showBanner && adContainer != null) {
            // Rimuovi eventuali banner esistenti
            if (currentAdView != null) {
                destroyBannerAd(currentAdView)
                null.also { currentAdView = it }
            }
            // Crea un nuovo banner
            currentAdView = setupBannerAd(context, adContainer, true)
            if (currentAdView != null) {
                Log.d("AdManager", "Banner creato con successo")
            } else {
                Log.w("AdManager", "Impossibile creare il banner")
            }
            // Carica altri annunci
            loadOcrAd(context)
            loadStatinoAd(context)
        } else {
            // Distruggi banner esistente
            destroyBannerAd(currentAdView)
            // Pulisci il container
            adContainer?.removeAllViews()
            // Disattiva altri annunci (se necessario)
            clearAds()
            Log.d("AdManager", "Annunci rimossi")
        }
        return currentAdView
    }

    private fun clearAds() {
        // Interrompi caricamento annunci aggiuntivi
        ocrRewardedAd?.fullScreenContentCallback = null
        ocrRewardedAd = null
        statinoRewardedAd?.fullScreenContentCallback = null
        statinoRewardedAd = null
        Log.d("AdManager", "Annunci aggiuntivi disattivati")
    }

    fun pauseBannerAd(adView: AdView?) {
        adView?.pause()
        Log.d("AdManager", "Banner in pausa")
    }

    fun resumeBannerAd(adView: AdView?) {
        adView?.resume()
        Log.d("AdManager", "Banner ripreso")
    }
}