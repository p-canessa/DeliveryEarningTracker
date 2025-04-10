package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Button
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

class ImageRecognitionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DataAdapter
    private lateinit var fields: MutableList<FieldItem>
    private lateinit var orderData: OrderData
    private lateinit var dbHelper: DatabaseHelper
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var toggleUseDate:Switch
    private lateinit var currencySymbol: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var selectFileLauncher: ActivityResultLauncher<Intent>
    private var ocrUsesLeft = 0 // Contatore utilizzi OCR
    private var isAdsEnabled = DisableAds.VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_recognition)

        selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val selectedFileUri = data?.data // URI del file selezionato
                // Qui puoi gestire il file, ad esempio avviando il processo OCR
                startOCR(selectedFileUri)
            }
        }
        loadOcrUsesLeft()
        dbHelper = DatabaseHelper(this)
        isAdsEnabled = DisableAds.loadAdsEnabledState(this, dbHelper)


        // Carica l'annuncio all'avvio
        if (isAdsEnabled) {
            AdManager.loadOcrAd(this)}

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

        imageView = findViewById(R.id.image_view)
        recyclerView = findViewById(R.id.data_recycler_view)
        toggleUseDate = findViewById(R.id.toggleUseDate)
        this.toggleUseDate.isChecked = false

        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        currencySymbol = CurrencyFormatter.getCurrencySymbol()

        fields = mutableListOf()

        this.adapter = DataAdapter(fields) { position, newValue ->
            Log.d("ImageRecognitionActivity", "Updating position $position to $newValue")
            when (position) {
                0 -> orderData.data = newValue
                1 -> {
                    orderData.tempoImpiegato = newValue.toIntOrNull() ?: 0
                    recalculateTotals()
                }
                2 -> orderData.numeroOrdini = newValue.toIntOrNull() ?: 0
                3 -> {
                    orderData.pagaBase = newValue.toDoubleOrNull() ?: 0.0
                    recalculateTotals()
                }
                4 -> orderData.riscossiContanti = newValue.toDoubleOrNull() ?: 0.0
                5 -> {
                    orderData.pagaExtra = newValue.toDoubleOrNull() ?: 0.0
                    recalculateTotals()
                }
                6 -> {
                    orderData.mancia = newValue.toDoubleOrNull() ?: 0.0
                    recalculateTotals()
                }
                7 -> {
                    orderData.manciaContanti = newValue.toDoubleOrNull() ?: 0.0
                    recalculateTotals()
                }
                // Positions 8 (pagaTotale) and 9 (pagaOraria) are calculated, not directly edited
            }
            updateUI()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        // Gestione del click sulla TextView
        tvSelectedDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    // Aggiorna la TextView con la data selezionata
                    val date = Calendar.getInstance()
                    date.set(selectedYear, selectedMonth, selectedDay)
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
                    tvSelectedDate.text = formattedDate
                },
                year, month, day
            )
            datePickerDialog.show()
        }
        updateButtonText()

        findViewById<Button>(R.id.select_file_button).setOnClickListener {
            if (isAdsEnabled) {
                if (ocrUsesLeft > 0) {
                    selectFile() // Procedi se ci sono utilizzi disponibili
                } else {
                    if (AdManager.isOcrAdLoaded()) {
                        AdManager.showOcrAd(this,
                            onRewardEarned = { rewardItem ->
                                val rewardAmount = rewardItem.amount
                                ocrUsesLeft = rewardAmount
                                saveOcrUsesLeft()
                            },
                            onAdClosed = {
                                selectFile()
                            }
                        )
                    } else {
                        // Mostra un messaggio all'utente
                        Toast.makeText(this, "Caricamento dell'ad in corso...", Toast.LENGTH_SHORT).show()
                        // Ricarica l'ad se non è pronto
                        AdManager.loadOcrAd(this)
                    }
                }
            } else {
                selectFile() // Nessun annuncio se gli annunci sono disattivati
            }
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            dbHelper.insertOrder(orderData)
            finish()
        }

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            finish()
        }
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

    private fun selectFile() {
        if ((ocrUsesLeft > 0)  || (!isAdsEnabled)) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            selectFileLauncher.launch(Intent.createChooser(intent, getString(R.string.seleziona_immagine)))
        }
    }

    private fun updateButtonText() {
        val buttonText = if (isAdsEnabled && ocrUsesLeft > 0) {
            getString(R.string.button_ocr_text_uses, ocrUsesLeft)
        } else if (isAdsEnabled) {
            getString(R.string.button_ocr_text_ads)
        } else {
            getString(R.string.button_ocr_text_noAds)
        }
        findViewById<Button>(R.id.select_file_button).text = buttonText
    }

    private fun updateUI() {
        // Update the fields list with the new values
        fields[8].value = CurrencyFormatter.format(orderData.pagaTotale)
        fields[9].value = CurrencyFormatter.format(orderData.pagaOraria)
        updateButtonText()
        adapter.notifyItemRangeChanged(8,2) // Refresh the entire RecyclerView
    }

    private fun recalculateTotals() {
        // Calculate pagaTotale as the sum of base, extra, and tip
        orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia + orderData.manciaContanti

        // Calculate pagaOraria, avoiding division by zero
        if (orderData.tempoImpiegato > 0) {
            orderData.pagaOraria = (orderData.pagaTotale * 60) / orderData.tempoImpiegato
        } else {
            orderData.pagaOraria = 0.0
        }
    }

    // Funzioni per gestire la persistenza degli utilizzi OCR
    private fun saveOcrUsesLeft() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPref.edit { putInt("ocr_uses_left", ocrUsesLeft) }
        updateButtonText()
    }

    private fun loadOcrUsesLeft() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        ocrUsesLeft = sharedPref.getInt("ocr_uses_left", 0)
    }

    private fun startOCR(selectedUri: Uri?) {
        if (selectedUri != null) {
            try {
                // Apri un InputStream per verificare la dimensione
                val inputStream = contentResolver.openInputStream(selectedUri)
                if (inputStream != null) {
                    // Controlla la dimensione del file prima di decodificare
                    val fileSize = inputStream.available()
                    val maxSize = 5 * 1024 * 1024 // 5 MB
                    if (fileSize > maxSize) {
                        inputStream.close()
                        orderData = OrderData() // Resetta i dati
                        updateFields() // Aggiorna i campi (vuoti)
                        Toast.makeText(this, getString(R.string.ocr_too_large), Toast.LENGTH_LONG).show()
                        Log.d("MainActivity", "Immagine troppo grande: $fileSize byte")
                        return
                    }

                    // Decodifica il Bitmap
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close() // Chiudi lo stream
                    if (bitmap == null) {
                        orderData = OrderData() // Resetta i dati
                        updateFields() // Aggiorna i campi (vuoti)
                        Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                        Log.e("MainActivity", "Errore: Bitmap nullo")
                        return
                    }

                    // Imposta il Bitmap nell'ImageView
                    imageView.setImageBitmap(bitmap)

                    // Esegui l'OCR passando il Bitmap
                    OCRHelper.extractDataFromImage(
                        this,
                        bitmap, // Passa il Bitmap invece di ricaricarlo
                        selectedUri, // Uri serve per extractDateFromUri
                        tvSelectedDate.text.toString(),
                        toggleUseDate.isChecked
                    ) { result ->
                        when (result.codice) {
                            OcrResultCode.SUCCESS -> {
                                orderData = result.orderData
                                ocrUsesLeft--
                                saveOcrUsesLeft() // Salva lo stato aggiornato
                                updateFields() // Aggiorna i campi con i dati estratti
                                Toast.makeText(this, "OCR completato con successo!", Toast.LENGTH_SHORT).show()
                            }
                            OcrResultCode.FILE_TOO_LARGE -> {
                                // Questo caso non dovrebbe verificarsi qui, ma lo lasciamo per completezza
                                orderData = OrderData()
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_too_large), Toast.LENGTH_LONG).show()
                                Log.d("MainActivity", "Immagine troppo grande (controllo ridondante)")
                            }
                            OcrResultCode.IMAGE_LOAD_FAILED -> {
                                orderData = OrderData()
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                                Log.e("MainActivity", "Errore caricamento immagine")
                            }
                            OcrResultCode.OCR_PROCESSING_FAILED -> {
                                orderData = OrderData()
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_processing_failed), Toast.LENGTH_LONG).show()
                                Log.e("MainActivity", "Errore elaborazione OCR")
                            }
                        }
                    }
                } else {
                    orderData = OrderData()
                    updateFields()
                    Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "InputStream nullo")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                orderData = OrderData()
                updateFields()
                Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Errore in startOCR: ${e.message}")
            }
        }
    }

    private fun updateFields() {
        fields.clear()
        fields.add(FieldItem(getString(R.string.data), orderData.data, true))
        fields.add(FieldItem(getString(R.string.tempo_impiegato_ocr), orderData.tempoImpiegato.toString()))
        fields.add(FieldItem(getString(R.string.numero_ordini), orderData.numeroOrdini.toString()))
        fields.add(FieldItem(getString(R.string.paga_base, currencySymbol), CurrencyFormatter.format(orderData.pagaBase)))
        fields.add(FieldItem(getString(R.string.riscossi_contanti, currencySymbol), CurrencyFormatter.format(orderData.riscossiContanti)))
        fields.add(FieldItem(getString(R.string.paga_extra, currencySymbol), CurrencyFormatter.format(orderData.pagaExtra)))
        fields.add(FieldItem(getString(R.string.mancia, currencySymbol), CurrencyFormatter.format(orderData.mancia)))
        fields.add(FieldItem(getString(R.string.mancia_contanti, currencySymbol), CurrencyFormatter.format(orderData.manciaContanti)))
        fields.add(FieldItem(getString(R.string.paga_totale, currencySymbol), CurrencyFormatter.format(orderData.pagaTotale)))
        fields.add(FieldItem(getString(R.string.paga_oraria, currencySymbol), CurrencyFormatter.format(orderData.pagaOraria)))
        adapter.notifyItemRangeChanged(0, fields.size)
    }
}