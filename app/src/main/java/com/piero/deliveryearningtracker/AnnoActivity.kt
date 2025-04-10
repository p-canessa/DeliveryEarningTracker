package com.piero.deliveryearningtracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
import java.util.Locale

class AnnoActivity : AppCompatActivity() {

    private lateinit var spinnerAnno: Spinner
    private var anni: MutableList<Int> = mutableListOf()
    private var adView: AdView? = null
    private var isAdsEnabled = DisableAds.VALUE

    override fun onDestroy() {
        AdManager.destroyBannerAd(adView) // Pulizia
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anno)

        MobileAds.initialize(this) {}


        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.setupBannerAd(this, adContainer, isAdsEnabled)

        // Configura la toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Abilita la freccia di "indietro"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Gestisci il click sulla freccia
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Torna all'activity precedente
        }

        spinnerAnno = findViewById(R.id.spinner_anno)
        val dbHelper = DatabaseHelper(this)
        // Popola lo Spinner con i trimestri
        anni.addAll(dbHelper.getYears())

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, anni)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAnno.adapter = adapter

        // Imposta il listener per lo Spinner
        spinnerAnno.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val annoSelezionato = parent.getItemAtPosition(position).toString()

                val datiA = dbHelper.getAnnualData(annoSelezionato)
                aggiornaTableLayout(datiA.data)

                aggiornaConsegne(datiA.consegne)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Imposta il trimestre corrente come default
        spinnerAnno.setSelection(0)
    }

    // Infla il menu della toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                val intent = Intent(this, HelpActivity::class.java)
                intent.putExtra("calling_page_title", supportActionBar?.title.toString())
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun aggiornaConsegne(totalConsegne:  Int) {
        val tvConsegneFatte = findViewById<TextView>(R.id.consegne_totale)
        val tvPerBonus = findViewById<TextView>(R.id.next_bonus)

        val prossimoMultiplo = ((totalConsegne / 2000) + 1) * 2000
        val ordiniMancanti = if (totalConsegne < prossimoMultiplo) prossimoMultiplo - totalConsegne else 0
        val coloreTesto = if (totalConsegne >= 2000) Color.GREEN else Color.RED
        tvConsegneFatte.setTextColor(coloreTesto)
        tvConsegneFatte.text = String.format(Locale.getDefault(), "%d", totalConsegne)
        tvPerBonus.text = String.format(Locale.getDefault(), "%d",ordiniMancanti)

    }

    /** Aggiorna la TableLayout con i dati aggregati */
    private fun aggiornaTableLayout(dati: Map<String, Double>) {
        val tvOrdiniLordo = findViewById<TextView>(R.id.tv_ordini_lordo)
        val tvOrdiniNetto = findViewById<TextView>(R.id.tv_ordini_netto)
        val tvOrdiniIva = findViewById<TextView>(R.id.tv_ordini_iva)
        val tvIntegrazioniLordo = findViewById<TextView>(R.id.tv_integrazioni_lordo)
        val tvIntegrazioniNetto = findViewById<TextView>(R.id.tv_integrazioni_netto)
        val tvIntegrazioniIva = findViewById<TextView>(R.id.tv_integrazioni_iva)
        val tvManceLordo = findViewById<TextView>(R.id.tv_mance_lordo)
        val tvManceNetto = findViewById<TextView>(R.id.tv_mance_netto)
        val tvManceIva = findViewById<TextView>(R.id.tv_mance_iva)
        val tvTotaleLordo = findViewById<TextView>(R.id.tv_totale_lordo)
        val tvTotaleNetto = findViewById<TextView>(R.id.tv_totale_netto)
        val tvTotaleIva = findViewById<TextView>(R.id.tv_totale_iva)

        tvOrdiniLordo.text = CurrencyFormatter.format(dati["ordini_lordo"] ?: 0.0)
        tvOrdiniNetto.text = CurrencyFormatter.format(dati["ordini_netto"] ?: 0.0)
        tvOrdiniIva.text = CurrencyFormatter.format(dati["ordini_iva"] ?: 0.0)
        tvIntegrazioniLordo.text = CurrencyFormatter.format(dati["integrazioni_lordo"] ?: 0.0)
        tvIntegrazioniNetto.text = CurrencyFormatter.format(dati["integrazioni_netto"] ?: 0.0)
        tvIntegrazioniIva.text = CurrencyFormatter.format(dati["integrazioni_iva"] ?: 0.0)
        tvManceLordo.text = CurrencyFormatter.format(dati["mance_lordo"] ?: 0.0)
        tvManceNetto.text = CurrencyFormatter.format(dati["mance_netto"] ?: 0.0)
        tvManceIva.text = CurrencyFormatter.format(dati["mance_iva"] ?: 0.0)
        tvTotaleLordo.text = CurrencyFormatter.format(dati["totale_lordo"] ?: 0.0)
        tvTotaleNetto.text = CurrencyFormatter.format(dati["totale_netto"] ?: 0.0)
        tvTotaleIva.text = CurrencyFormatter.format(dati["totale_iva"] ?: 0.0)
    }
}