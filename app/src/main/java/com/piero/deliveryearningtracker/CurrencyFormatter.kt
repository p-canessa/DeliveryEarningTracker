package com.piero.deliveryearningtracker

import android.content.Context
import androidx.preference.PreferenceManager
import java.text.DecimalFormat
import java.util.Locale

object CurrencyFormatter {
    private lateinit var currencySymbol: String
    private lateinit var strFormatCurrency: String
    private var decimalFormat = DecimalFormat.getInstance(Locale.getDefault()) as DecimalFormat

    // Inizializza i valori leggendo le preferenze
    fun initialize(context: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        currencySymbol = sharedPref.getString("currency_symbol", "€") ?: "€"
        strFormatCurrency = if (currencySymbol == "€") {
            "%1\$s %2\$s" // "10.00 €"
        } else {
            "%2\$s%1\$s"  // "$10.00"
        }
        decimalFormat.applyPattern("0.00") // Sempre due decimali
    }

    // Metodo per formattare un valore
    fun format(value: Double): String {
        if (!this::currencySymbol.isInitialized || !this::strFormatCurrency.isInitialized) {
            throw IllegalStateException("CurrencyFormatter non è stato inizializzato. Chiama initialize() prima.")
        }
        return String.format(strFormatCurrency, decimalFormat.format(value), currencySymbol)
    }

    // Metodo opzionale per ottenere solo il simbolo
    fun getCurrencySymbol(): String {
        if (!this::currencySymbol.isInitialized) {
            throw IllegalStateException("CurrencyFormatter non è stato inizializzato. Chiama initialize() prima.")
        }
        return currencySymbol
    }
}