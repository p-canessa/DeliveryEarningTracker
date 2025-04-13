package com.piero.deliveryearningtracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Date
import android.widget.TextView
import java.io.InputStream
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import android.content.Context

// Data class per il risultato
data class PdfExtractionResult(
    val testo: String = "",
    val codice: ResultCode
)

// Enum per i codici di successo/errore
enum class ResultCode {
    SUCCESS,                // Estrazione riuscita e documento valido
    FILE_TOO_LARGE,         // File supera i 100 KB
    INVALID_FORMAT,         // Formato non valido (manca intestazione o righe successive)
    EXTRACTION_FAILED       // Errore generico durante l'estrazione
}

// Per la tabella "Ordini consegnati"
data class GuadagniGiornalieri(
    val data: Date,
    val numeroOrdini: Int,
    val totaleLordo: Double
)

/*
// Per la tabella "Modifiche e integrazioni"
data class Integrazione(
    val dettaglio: String,
    val totaleLordo: Double,
    val iva: Double,
    val importoIva: Double,
    val totale: Double
)

// Per la tabella "Prospetto finale"
data class RiepilogoFinale(
    val dettaglio: String,
    val importoLordo: Double,
    val iva: Double,
    val importoIva: Double,
    val totale: Double
)
*/

data class RiepilogoData(
    val month: String, // Es. "Dicembre 2024"
    val monthNumber: Int,
    val year: Int,
    val taxRegime: String, // "Ritenuta d'acconto", "Regime Forfettario", "Regime Ordinario"
    val documentNumber: String,
    val ordiniLordo: Double,
    val ordiniRitenutaAcconto: String?, // Es. "20%"
    val ordiniImportoRitenuta: Double?,
    val ordiniIva: String?, // Es. "22%"
    val ordiniImportoIva: Double?,
    val ordiniTotale: Double,
    val integrazioniLordo: Double,
    val integrazioniRitenutaAcconto: String?,
    val integrazioniImportoRitenuta: Double?,
    val integrazioniIva: String?,
    val integrazioniImportoIva: Double?,
    val integrazioniTotale: Double,
    val manceLordo: Double,
    val manceRitenutaAcconto: String?,
    val manceImportoRitenuta: Double?,
    val manceIva: String?,
    val manceImportoIva: Double?,
    val manceTotale: Double,
    val totaleLordo: Double,
    val totaleRitenutaAcconto: String?,
    val totaleImportoRitenuta: Double?,
    val totaleIva: String?,
    val totaleImportoIva: Double?,
    val totaleTotale: Double,
    val pagamentiContanti: Double?,
    val totaleDovuto: Double?
)



class PDFImport : AppCompatActivity() {

    private lateinit var btnCaricaPdf: Button
    private lateinit var textViewRisultati: TextView
    private lateinit var btnViewStatement: Button
    private var adView: AdView? = null
    private lateinit var dbHelper: DatabaseHelper
    private var isStatinoEnabled = false
    private var isAdsEnabled = DisableAds.VALUE // true per default
    private lateinit var selectPdfLauncher: ActivityResultLauncher<Intent>

    override fun onDestroy() {
        AdManager.destroyBannerAd(adView) // Pulizia
        adView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AdManager.resumeBannerAd(adView)
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)
    }

    override fun onPause() {
        AdManager.pauseBannerAd(adView)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pdf_import)

        MobileAds.initialize(this) {
            Log.d("AdMob", "Inizializzazione completata")
        }

        btnCaricaPdf = findViewById(R.id.btn_carica_pdf)
        textViewRisultati = findViewById(R.id.textview_risultati)
        btnViewStatement = findViewById(R.id.btn_view_statement)

        dbHelper = DatabaseHelper(this)

        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)

        loadStatinoEnabled()
        Log.d("PDFImport", "isStatinoEnabled caricato: $isStatinoEnabled")
        updateButtonText()
        AdManager.loadStatinoAd(this)

        selectPdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val uri = data?.data ?: return@registerForActivityResult

                val risultato = estraiTestoDalPdf(uri, context = this)
                when (risultato.codice) {
                    ResultCode.SUCCESS -> {
                        try {
                            val (guadagni, riepilogoData) = analizzaDatiPdf(risultato.testo)

                            if (dbHelper.isDocumentNumberExists(riepilogoData.documentNumber)) {
                                Toast.makeText(this, getString(R.string.pdf_duplicate), Toast.LENGTH_LONG).show()
                                textViewRisultati.text = getString(R.string.pdfImport_Statino_ko, getString(R.string.pdf_duplicate))
                                btnViewStatement.visibility = View.GONE
                                return@registerForActivityResult
                            }

                            val monthlySummaryId = dbHelper.insertMonthlySummary(riepilogoData)
                            if (monthlySummaryId != -1L) {
                                dbHelper.insertDailyOrders(guadagni, monthlySummaryId)
                                // Resetta isStatinoEnabled dopo un caricamento riuscito
                                isStatinoEnabled = false
                                saveStatinoEnabled()
                                updateButtonText()

                                val monthName = java.text.DateFormatSymbols(Locale.getDefault()).months[riepilogoData.monthNumber - 1]
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                val formattedMonth = "$monthName ${riepilogoData.year}"

                                textViewRisultati.text = getString(R.string.pdfImport_Statino_ok, riepilogoData.documentNumber, formattedMonth)

                                btnViewStatement.visibility = View.VISIBLE
                                btnViewStatement.setOnClickListener {
                                    val intent = Intent(this, ShowMontlyStatement::class.java).apply {
                                        putExtra("summary_id", monthlySummaryId.toInt())
                                    }
                                    startActivity(intent)
                                    finish()
                                }
                            } else {
                                throw Exception("Errore durante l'inserimento nel database")
                            }
                        } catch (e: Exception) {
                            Log.e("PDFImport", "Errore nell'analisi o inserimento: ${e.message}")
                            textViewRisultati.text = getString(R.string.pdfImport_Statino_ko, e.message)
                            btnViewStatement.visibility = View.GONE
                        }
                    }
                    ResultCode.FILE_TOO_LARGE -> {
                        Log.d("PDFImport", "File troppo grande (>100 KB)")
                        val errorMsg = getString(R.string.pdf_too_large)
                        textViewRisultati.text = getString(R.string.pdfImport_Statino_ko, errorMsg)
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        btnViewStatement.visibility = View.GONE
                    }
                    ResultCode.INVALID_FORMAT -> {
                        Log.d("PDFImport", "Formato del file non valido")
                        val errorMsg = getString(R.string.pdf_invalid_format)
                        textViewRisultati.text = getString(R.string.pdfImport_Statino_ko, errorMsg)
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        btnViewStatement.visibility = View.GONE
                    }
                    ResultCode.EXTRACTION_FAILED -> {
                        Log.e("PDFImport", "Errore durante l'estrazione del testo")
                        val errorMsg = getString(R.string.pdf_extraction_failed)
                        textViewRisultati.text = getString(R.string.pdfImport_Statino_ko, errorMsg)
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        btnViewStatement.visibility = View.GONE
                    }
                }
            }
            updateButtonText()
            Log.d("PDFImport", "Dopo selezione file - isStatinoEnabled: $isStatinoEnabled, isAdsEnabled: $isAdsEnabled")
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnCaricaPdf.setOnClickListener {
            Log.d("PDFImport", "Click su btnCaricaPdf - isAdsEnabled: $isAdsEnabled, isStatinoEnabled: $isStatinoEnabled")
            if (isAdsEnabled && !isStatinoEnabled) {
                if (AdManager.isStatinoAdLoaded()) {
                    AdManager.showStatinoAd(this,
                        onRewardEarned = {
                            isStatinoEnabled = true
                            saveStatinoEnabled()
                            updateButtonText()
                            Log.d("PDFImport", "Reward ricevuto - isStatinoEnabled: $isStatinoEnabled")
                            selezionaFilePdf()
                        },
                        onAdClosed = {
                            updateButtonText()
                            Log.d("PDFImport", "Annuncio chiuso - isStatinoEnabled: $isStatinoEnabled")
                        }
                    )
                } else {
                    Toast.makeText(this, "Caricamento dell'ad in corso...", Toast.LENGTH_SHORT).show()
                    AdManager.loadStatinoAd(this)
                }
            } else {
                selezionaFilePdf()
                Log.d("PDFImport", "Caricamento diretto senza annuncio")
            }
        }
    }

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

    private fun selezionaFilePdf() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf"
        }
        selectPdfLauncher.launch(intent)
    }

    private fun saveStatinoEnabled() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPref.edit { putBoolean("statino_enabled", isStatinoEnabled) }
    }

    private fun loadStatinoEnabled() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        isStatinoEnabled = sharedPref.getBoolean("statino_enabled", false)
    }

    private fun updateButtonText() {
        btnCaricaPdf.text = if (!isAdsEnabled || isStatinoEnabled) {
            getString(R.string.pdfimport_button_text_noAds) // "Carica"
        } else {
            getString(R.string.pdfimport_button_text) // "Guarda un ad per caricare"
        }
    }

    private fun estraiTestoDalPdf(uri: Uri, context: Context): PdfExtractionResult {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        return try {
            inputStream?.let {
                // Verifica dimensione file (max 100 KB)
                val fileSize = it.available()
                val maxSize = 100 * 1024 // 100 KB
                if (fileSize > maxSize) {
                    it.close()
                    return PdfExtractionResult(codice = ResultCode.FILE_TOO_LARGE)
                }

                // Inizializza PDFBox
                PDFBoxResourceLoader.init(context)
                val documento = PDDocument.load(it)

                // Limita l'estrazione alle prime 3 pagine
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = 3.coerceAtMost(documento.numberOfPages) // Non superare il numero di pagine totali
                }
                val testo = stripper.getText(documento)

                // Chiudi risorse
                documento.close()
                it.close()

                // Verifica validità del formato
                if (!isTestoValido(testo)) {
                    return PdfExtractionResult(testo = testo, codice = ResultCode.INVALID_FORMAT)
                }

                // Log del testo e ritorno con successo
                Log.d("PDFImport", testo)
                PdfExtractionResult(testo = testo, codice = ResultCode.SUCCESS)
            } ?: PdfExtractionResult(codice = ResultCode.EXTRACTION_FAILED)
        } catch (e: Exception) {
            inputStream?.close()
            Log.e("PDFImport", "Errore durante l'estrazione: ${e.message}")
            PdfExtractionResult(codice = ResultCode.EXTRACTION_FAILED)
        }
    }

    // Funzione per validare il testo
    private fun isTestoValido(testo: String): Boolean {
        val lines = testo.lines()
        val headerIndex = lines.indexOfFirst { it.contains("Deliveroo Italy Srl") }

        // Controlla se l'intestazione esiste e se ci sono almeno 3 righe successive
        return headerIndex != -1 && (headerIndex + 3) < lines.size
    }

    private fun analizzaDatiPdf(testo: String): Pair<List<GuadagniGiornalieri>, RiepilogoData> {
        val righe = testo.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var documentNumber = ""
        val listaGuadagni = mutableListOf<GuadagniGiornalieri>()
        val taxRegime = detectTaxRegime(testo)
        val formatoData = SimpleDateFormat("dd MMMM yyyy", Locale.ITALIAN)
        var month = ""
        var sezioneCorrente: String? = null
        var inRiepilogoPostTotale = false

        var ordiniLordo = 0.0
        var ordiniRitenutaAcconto: String? = null
        var ordiniImportoRitenuta: Double? = null
        var ordiniIva: String? = null
        var ordiniImportoIva: Double? = null
        var ordiniTotale = 0.0
        var integrazioniLordo = 0.0
        var integrazioniRitenutaAcconto: String? = null
        var integrazioniImportoRitenuta: Double? = null
        var integrazioniIva: String? = null
        var integrazioniImportoIva: Double? = null
        var integrazioniTotale = 0.0
        var manceLordo = 0.0
        var manceRitenutaAcconto: String? = null
        var manceImportoRitenuta: Double? = null
        var manceIva: String? = null
        var manceImportoIva: Double? = null
        var manceTotale = 0.0
        var totaleLordo = 0.0
        var totaleRitenutaAcconto: String? = null
        var totaleImportoRitenuta: Double? = null
        var totaleIva: String? = null
        var totaleImportoIva: Double? = null
        var totaleTotale = 0.0
        var pagamentiContanti: Double? = null
        var totaleDovuto: Double? = null

        for (riga in righe) {
            if (riga.startsWith("Numero del documento:")) {
                documentNumber = riga.removePrefix("Numero del documento:").trim()
                continue
            }
            if (riga == "1" || riga == "2" || riga.startsWith("Dettaglio Importo lordo") || riga.startsWith("Giorno Data")) continue

            if (riga.startsWith("Periodo di riferimento:")) {
                val dateRange = riga.removePrefix("Periodo di riferimento:").trim()
                val endDate = dateRange.split(" - ")[1]
                month = endDate.split(" ")[1] + " " + endDate.split(" ")[2]
                continue
            }

            if (sezioneCorrente == null && !inRiepilogoPostTotale) {
                when {
                    riga == "Ordini consegnati (inclusi eventuali pagamenti extra)" -> sezioneCorrente = "Ordini"
                    riga == "Modifiche e integrazioni" -> sezioneCorrente = "Integrazioni"
                    riga.contains("Prospetto finale") -> sezioneCorrente = "Riepilogo"
                }
                continue
            }

            val parole = riga.split("\\s+".toRegex())

            if (sezioneCorrente != null && !inRiepilogoPostTotale) {
                try {
                    when (sezioneCorrente) {
                        "Ordini" -> {
                            if (riga.startsWith("Totale")) {
                                ordiniTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                sezioneCorrente = null
                            } else if (parole.size >= 6) {
                                val dataStr = "${parole[1]} ${parole[2]} ${parole[3]}"
                                val numeroOrdini = parole[4].toInt()
                                val guadagnoGiornaliero = parole[5].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                val data = formatoData.parse(dataStr)
                                if (data != null) {
                                    listaGuadagni.add(GuadagniGiornalieri(data, numeroOrdini, guadagnoGiornaliero))
                                } else {
                                    Log.e("PDFImport", "Errore nel parsing della data: $dataStr")
                                }
                            }
                        }
                        "Integrazioni" -> {
                            if (riga.startsWith("Totale")) {
                                integrazioniTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                integrazioniLordo = parole[1].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                if (taxRegime == "Ritenuta d'acconto") {
                                    integrazioniImportoRitenuta = parole[parole.size - 2].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                } else {
                                    integrazioniIva = "0%"
                                    integrazioniImportoIva = 0.0
                                }
                                sezioneCorrente = null
                            }
                        }
                        "Riepilogo" -> {
                            when {
                                riga.startsWith("Ordini") -> {
                                    ordiniLordo = parole[parole.size - 4].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    ordiniTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    if (taxRegime == "Ritenuta d'acconto") {
                                        ordiniRitenutaAcconto = parole[parole.size - 3]
                                        ordiniImportoRitenuta = parole[parole.size - 2].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    } else {
                                        ordiniIva = "0%"
                                        ordiniImportoIva = 0.0
                                    }
                                }
                                riga.startsWith("Modifiche e integrazioni") -> {
                                    integrazioniLordo = parole[parole.size - 4].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    integrazioniTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    if (taxRegime == "Ritenuta d'acconto") {
                                        integrazioniRitenutaAcconto = parole[parole.size - 3]
                                        integrazioniImportoRitenuta = parole[parole.size - 2].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    } else {
                                        integrazioniIva = "0%"
                                        integrazioniImportoIva = 0.0
                                    }
                                }
                                riga.startsWith("Mance") -> {
                                    manceLordo = parole[parole.size - 4].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    manceTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    if (taxRegime == "Ritenuta d'acconto") {
                                        manceRitenutaAcconto = parole[parole.size - 3]
                                        manceImportoRitenuta = parole[parole.size - 2].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    } else {
                                        manceIva = "0%"
                                        manceImportoIva = 0.0
                                    }
                                }
                                riga.startsWith("Totale") && totaleTotale == 0.0 -> {
                                    totaleLordo = parole[1].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    totaleTotale = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    if (taxRegime == "Ritenuta d'acconto") {
                                        totaleRitenutaAcconto = parole[parole.size - 3]
                                        totaleImportoRitenuta = parole[parole.size - 2].replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                                    } else {
                                        totaleIva = "0%"
                                        totaleImportoIva = 0.0
                                    }
                                    inRiepilogoPostTotale = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("PDFImport", "Errore nella riga: $riga, ${e.message}")
                }
            }

            if (inRiepilogoPostTotale) {
                if (riga.startsWith("Pagamenti in contanti già riscossi")) {
                    pagamentiContanti = parole.last().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                } else if (riga.startsWith("Totale dovuto:")) {
                    totaleDovuto = riga.split(":").last().trim().replace("€", "").replace(",", "").toDoubleOrNull() ?: 0.0
                    inRiepilogoPostTotale = false
                }
            }
        }
        val (monthNumber, year) = convertMonthToDate(month)

        val riepilogo = RiepilogoData(
            month = month,
            monthNumber = monthNumber,
            year = year,
            taxRegime = taxRegime,
            documentNumber = documentNumber,
            ordiniLordo = ordiniLordo,
            ordiniRitenutaAcconto = ordiniRitenutaAcconto,
            ordiniImportoRitenuta = ordiniImportoRitenuta,
            ordiniIva = ordiniIva,
            ordiniImportoIva = ordiniImportoIva,
            ordiniTotale = ordiniTotale,
            integrazioniLordo = integrazioniLordo,
            integrazioniRitenutaAcconto = integrazioniRitenutaAcconto,
            integrazioniImportoRitenuta = integrazioniImportoRitenuta,
            integrazioniIva = integrazioniIva,
            integrazioniImportoIva = integrazioniImportoIva,
            integrazioniTotale = integrazioniTotale,
            manceLordo = manceLordo,
            manceRitenutaAcconto = manceRitenutaAcconto,
            manceImportoRitenuta = manceImportoRitenuta,
            manceIva = manceIva,
            manceImportoIva = manceImportoIva,
            manceTotale = manceTotale,
            totaleLordo = totaleLordo,
            totaleRitenutaAcconto = totaleRitenutaAcconto,
            totaleImportoRitenuta = totaleImportoRitenuta,
            totaleIva = totaleIva,
            totaleImportoIva = totaleImportoIva,
            totaleTotale = totaleTotale,
            pagamentiContanti = pagamentiContanti,
            totaleDovuto = totaleDovuto
        )
        return Pair(listaGuadagni, riepilogo)
    }

    private fun detectTaxRegime(testo: String): String {
        return when {
            testo.contains("Ritenuta d'acconto") -> "Ritenuta d'acconto"
            testo.contains("IVA") && testo.contains("0%") -> "Regime Forfettario"
            testo.contains("IVA") && testo.contains("22%") -> "Regime Ordinario"
            else -> "Sconosciuto"
        }
    }

    private fun convertMonthToDate(month: String): Pair<Int, Int> {
        val year = month.substringAfter(" ").toInt()
        val monthNumber = when (val monthName = month.substringBefore(" ").lowercase()) {
            "gennaio" -> 1
            "febbraio" -> 2
            "marzo" -> 3
            "aprile" -> 4
            "maggio" -> 5
            "giugno" -> 6
            "luglio" -> 7
            "agosto" -> 8
            "settembre" -> 9
            "ottobre" -> 10
            "novembre" -> 11
            "dicembre" -> 12
            else -> throw IllegalArgumentException("Mese non valido: $monthName")
        }
        return Pair(monthNumber, year)
    }
}