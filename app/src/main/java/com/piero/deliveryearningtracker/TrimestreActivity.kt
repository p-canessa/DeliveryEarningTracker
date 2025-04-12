package com.piero.deliveryearningtracker

import android.content.Intent
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
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.*

class TrimestreActivity : AppCompatActivity() {

    private lateinit var spinnerTrimestre: Spinner
    private var trimestri: MutableList<String> = mutableListOf()
    private var adView: AdView? = null
    private var dbHelper: DatabaseHelper = DatabaseHelper(this)

    override fun onDestroy() {
        AdManager.destroyBannerAd(adView)
        adView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trimestre)

        MobileAds.initialize(this) {}

        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)

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
        spinnerTrimestre = findViewById(R.id.spinner_trimestre)
        val dbHelper = DatabaseHelper(this)
        // Popola lo Spinner con i trimestri
        trimestri.addAll(generaTrimestriIniziali())
        trimestri.add("Altri...")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, trimestri)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTrimestre.adapter = adapter

        // Imposta il listener per lo Spinner
        spinnerTrimestre.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val trimestreSelezionato = parent.getItemAtPosition(position).toString()
                if (trimestreSelezionato == "Altri...") {
                    // Rimuovi "Altri..." temporaneamente
                    trimestri.remove("Altri...")
                    // Aggiungi altri 5 trimestri
                    val nuoviTrimestri = generaAltriTrimestri()
                    trimestri.addAll(nuoviTrimestri)
                    // Riaggiungi "Altri..." alla fine
                    trimestri.add("Altri...")
                    // Aggiorna lo spinner
                    adapter.notifyDataSetChanged()
                    // Seleziona l'ultimo trimestre aggiunto prima di "Altri..."
                    spinnerTrimestre.setSelection(trimestri.size - 2)
                } else {
                    val parti = trimestreSelezionato.split(" ") // Es. "Q1 2024"
                    val q = parti[0].substring(1).toInt() // Es. "1"
                    val anno = parti[1].toInt() // Es. "2024"
                    val mesi = when {
                        q in 1..4 -> ((q - 1) * 3 + 1..(q - 1) * 3 + 3).toList()
                        else -> emptyList()
                    }
                    val datiAggregati = dbHelper.getTrimestreData(mesi, anno)
                    aggiornaTableLayout(datiAggregati)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Imposta il trimestre corrente come default
        spinnerTrimestre.setSelection(0)
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

    /** Genera la lista dei trimestri (corrente + 5 precedenti + "Altri...") */
    private fun generaTrimestriIniziali(): List<String> {
        val trimestri = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        val annoCorrente = calendar.get(Calendar.YEAR)
        val meseCorrente = calendar.get(Calendar.MONTH) + 1

        // Calcola il trimestre corrente
        val trimestreCorrente = (meseCorrente - 1) / 3 + 1
        trimestri.add("Q$trimestreCorrente $annoCorrente")

        // Aggiungi i 5 trimestri precedenti
        for (i in 1..5) {
            calendar.add(Calendar.MONTH, -3)
            val anno = calendar.get(Calendar.YEAR)
            val mese = calendar.get(Calendar.MONTH) + 1
            val trimestre = (mese - 1) / 3 + 1
            trimestri.add("Q$trimestre $anno")
        }
        return trimestri
    }

    /** Genera altri 5 trimestri precedenti */
    private fun generaAltriTrimestri(): List<String> {
        val nuoviTrimestri = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        // Imposta il calendar al trimestre più vecchio nella lista
        val ultimoTrimestre = trimestri.last().split(" ")
        val ultimoAnno = ultimoTrimestre[1].toInt()
        val ultimoQ = ultimoTrimestre[0].substring(1).toInt()
        calendar.set(ultimoAnno, (ultimoQ - 1) * 3, 1)

        for (i in 1..5) {
            calendar.add(Calendar.MONTH, -3)
            val anno = calendar.get(Calendar.YEAR)
            val mese = calendar.get(Calendar.MONTH) + 1
            val trimestre = (mese - 1) / 3 + 1
            nuoviTrimestri.add("Q$trimestre $anno")
        }
        return nuoviTrimestri
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