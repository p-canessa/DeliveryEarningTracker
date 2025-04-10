package com.piero.deliveryearningtracker

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.util.Locale

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        // Recupera il titolo della pagina chiamante
        val callingPageTitle = intent.getStringExtra("calling_page_title") ?: getString(R.string.app_name)

        // Configura la toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = String.format("%s - %s", getString(R.string.help_title), callingPageTitle)

        // Gestisci il click sulla freccia
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Converti il titolo in un nome file (rimuovi spazi e converti in minuscolo)
        val callingPage = callingPageTitle.replace(" ", "_").lowercase()

        // Configura la WebView
        val webView: WebView = findViewById(R.id.webview_help)
        //webView.settings.javaScriptEnabled = true

        // Determina la lingua corrente
        val locale = Locale.getDefault().language
        val helpFilePath = getHelpFilePath(callingPage, locale)

        Log.d("Help_Activity", "Help file: file:///android_asset/help/${helpFilePath}")
        // Carica il file HTML
        webView.loadUrl("file:///android_asset/help/$helpFilePath")
    }

    private fun getHelpFilePath(callingPage: String, locale: String): String {
        // Costruisci il percorso del file HTML in base alla lingua e al titolo della pagina
        Log.d("Help_Activity", "Help chiamato con callingPage: ${callingPage}; e locale: $locale")
        val fileName = when (callingPage) {
            getString(R.string.app_name).replace(" ", "_").lowercase() -> "main.htm"
            getString(R.string.label_visualizza_statino).replace(" ", "_").lowercase() -> "show_monthly_statement.htm"
            getString(R.string.label_importazone_statino).replace(" ", "_").lowercase() -> "import_paystub.htm"
            getString(R.string.label_trimestrale).replace(" ", "_").lowercase() -> "quarter_report.htm"
            getString(R.string.label_annuale).replace(" ", "_").lowercase() -> "year_report.htm"
            getString(R.string.label_riconciliazione).replace(" ", "_").lowercase() -> "reconciliation.htm"
            getString(R.string.label_esportazione).replace(" ", "_").lowercase() -> "export_data.htm"
            getString(R.string.label_inserimento_ordine).replace(" ", "_").lowercase() -> "insert_order.htm"
            getString(R.string.settings_title).replace(" ", "_").lowercase() -> "settings.htm"
            // Aggiungi altri casi per altre pagine
            else -> "main.htm" // Default
        }

        // Controlla se esiste il file nella lingua corrente
        val localizedPath = "$locale/$fileName"
        val defaultPath = "en/$fileName" // Fallback in inglese

        return if (assets.list("help/$locale")?.contains(fileName) == true) {
            localizedPath
        } else {
            defaultPath
        }
    }
}