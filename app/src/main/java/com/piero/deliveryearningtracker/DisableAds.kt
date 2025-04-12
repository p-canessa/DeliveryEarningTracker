package com.piero.deliveryearningtracker

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

object DisableAds {
    val VALUE: Boolean get() = BuildConfig.ENABLE_ADS // True per versione pubblica, False per versione personale

    fun loadAdsEnabledState(context: Context, dbHelper: DatabaseHelper): Boolean {
        if (!VALUE) {
            Log.d("DisableAds", "Annunci disabilitati per BuildConfig.ENABLE_ADS=false")
            return false // Versione personale, annunci sempre disabilitati
        }
        dbHelper.updateAdsEnabledState()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdsEnabled = sharedPref.getBoolean("ads_enabled", true)
        Log.d("DisableAds", "Stato annunci: isAdsEnabled=$isAdsEnabled")
        return isAdsEnabled
    }
}