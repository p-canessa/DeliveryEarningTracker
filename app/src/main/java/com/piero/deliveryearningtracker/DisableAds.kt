package com.piero.deliveryearningtracker

import android.content.Context
import androidx.preference.PreferenceManager

object DisableAds {
     val VALUE :Boolean get() = BuildConfig.ENABLE_ADS // True per versione pubblica, False per versione personale senza annunci

    fun loadAdsEnabledState(context: Context, dbHelper: DatabaseHelper): Boolean {
        return if (!VALUE) {
            false // Se VALUE è false, gli annunci sono sempre disabilitati
        } else {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            dbHelper.updateAdsEnabledState() // Usa isSubscriptionActive internamente
            sharedPref.getBoolean("ads_enabled", true)
        }
    }
}