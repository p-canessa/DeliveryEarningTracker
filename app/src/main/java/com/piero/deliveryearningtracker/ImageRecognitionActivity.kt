package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piero.deliveryearningtracker.utils.TimeUtils
import java.sql.Time
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale


class ImageRecognitionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DataAdapter
    private lateinit var fields: MutableList<FieldItem>
    private lateinit var orderData: OrderData
    private lateinit var dbHelper: DatabaseHelper
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var toggleUseDate: Switch
    private lateinit var currencySymbol: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var selectFileLauncher: ActivityResultLauncher<Intent>
    private var ocrUsesLeft = 0 // Contatore utilizzi OCR
    private var isAdsEnabled = DisableAds.VALUE
    private lateinit var candidateOrders: List<CandidateOrder>
    private val relatedOrders = mutableMapOf<Long, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_recognition)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val selectedFileUri = data?.data
                startOCR(selectedFileUri)
            }
        }
        loadOcrUsesLeft()
        dbHelper = DatabaseHelper.getInstance(this)
        isAdsEnabled = DisableAds.loadAdsEnabledState(this)
        AdManager.updateAds(this, null, null, false)

        // Configura la toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        imageView = findViewById(R.id.image_view)
        recyclerView = findViewById(R.id.data_recycler_view)
        toggleUseDate = findViewById(R.id.toggleUseDate)
        toggleUseDate.isChecked = false

        currencySymbol = CurrencyFormatter.getCurrencySymbol()

        fields = mutableListOf()
        orderData = OrderData() // Initialize orderData

        this.adapter = DataAdapter(fields,
            onValueChanged = { key, newValue ->
                Log.d("ImageRecognitionActivity", "Updating $key to $newValue")
                when (key) {
                    FieldKeys.DATA -> orderData.data = newValue
                    FieldKeys.TEMPO_IMPIEGATO -> {
                        orderData.tempoImpiegato = newValue.toIntOrNull() ?: 0
                        recalculateTotals()
                    }
                    FieldKeys.NUMERO_ORDINI -> {
                        orderData.numeroOrdini = newValue.toIntOrNull() ?: 0
                        updateFields() // Update fields to add/remove WARNING_DOUBLE_ORDER
                    }
                    FieldKeys.PAGA_BASE -> {
                        orderData.pagaBase = newValue.toDoubleOrNull() ?: 0.0
                        recalculateTotals()
                    }
                    FieldKeys.RISCOSSI_CONTANTI -> orderData.riscossiContanti = newValue.toDoubleOrNull() ?: 0.0
                    FieldKeys.PAGATO_CONTANTI_RISTORANTE -> {
                        orderData.pagatoContantiRistorante = newValue.toDoubleOrNull() ?: 0.0
                    }
                    FieldKeys.PAGA_EXTRA -> {
                        orderData.pagaExtra = newValue.toDoubleOrNull() ?: 0.0
                        recalculateTotals()
                    }
                    FieldKeys.MANCIA -> {
                        orderData.mancia = newValue.toDoubleOrNull() ?: 0.0
                        recalculateTotals()
                    }
                    FieldKeys.MANCIA_CONTANTI -> {
                        orderData.manciaContanti = newValue.toDoubleOrNull() ?: 0.0
                        recalculateTotals()
                    }
                    FieldKeys.START_TIME -> {
                        try {
                            orderData.startTime = Time.valueOf("$newValue:00")
                            recalculateTotals()
                        } catch (_: IllegalArgumentException) {
                            Log.e("ImageRecognitionActivity", "Invalid start time format: $newValue")
                            Toast.makeText(this, "Formato ora non valido: $newValue", Toast.LENGTH_SHORT).show()
                        }
                    }
                    FieldKeys.END_TIME -> {
                        try {
                            orderData.endTime = Time.valueOf("$newValue:00")
                            recalculateTotals()
                        } catch (_: IllegalArgumentException) {
                            Log.e("ImageRecognitionActivity", "Invalid end time format: $newValue")
                            Toast.makeText(this, "Formato ora non valido: $newValue", Toast.LENGTH_SHORT).show()
                        }
                    }
                    FieldKeys.ORDER_STRATEGY -> {
                        orderData.orderStrategy = newValue.toIntOrNull() ?: OrderStrategyConstants.NORMAL
                    }
                    FieldKeys.RISTORANTE -> orderData.ristorante = newValue
                    // WARNING_DOUBLE_ORDER is not editable
                    // batchMasterOrderID is hidden
                }
                updateUI()
            },
            onValidationChanged = { isValid ->
                findViewById<Button>(R.id.save_button).isEnabled = isValid
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        tvSelectedDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val date = Calendar.getInstance()
                    date.set(selectedYear, selectedMonth, selectedDay)
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
                    tvSelectedDate.text = formattedDate
                    orderData.data = formattedDate // Update orderData.data
                },
                year, month, day
            )
            datePickerDialog.show()
        }
        updateButtonText()

        findViewById<Button>(R.id.select_file_button).setOnClickListener {
            if (isAdsEnabled) {
                if (ocrUsesLeft > 0) {
                    selectFile()
                } else {
                    if (AdManager.isOcrAdLoaded()) {
                        AdManager.showOcrAd(this,
                            onRewardEarned = { rewardItem ->
                                ocrUsesLeft = rewardItem.amount
                                saveOcrUsesLeft()
                            },
                            onAdClosed = { selectFile() }
                        )
                    } else {
                        Toast.makeText(this, "Caricamento dell'ad in corso...", Toast.LENGTH_SHORT).show()
                        AdManager.loadOcrAd(this)
                    }
                }
            } else {
                selectFile()
            }
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            if (validateOrderData()) {
                dbHelper.insertOrder(orderData, relatedOrders)
                finish()
            } else {
                Toast.makeText(this, "Dati non validi, controlla i campi", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            finish()
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

    private fun selectFile() {
        if (ocrUsesLeft > 0 || !isAdsEnabled) {
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
        // Update read-only fields
        fields.find { it.key == FieldKeys.PAGA_TOTALE }?.value = CurrencyFormatter.format(orderData.pagaTotale)
        fields.find { it.key == FieldKeys.PAGA_ORARIA }?.value = CurrencyFormatter.format(orderData.pagaOraria)
        adapter.notifyDataSetChanged() // Refresh all fields
    }

    private fun recalculateTotals() {
        // Calculate pagaTotale
        orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia + orderData.manciaContanti
        val start = TimeUtils.timeToMinutes(orderData.startTime)
        val end = TimeUtils.timeToMinutes(orderData.endTime)
        var totalPaga = orderData.pagaTotale
        Log.d("recalculateTotals", "Tempo Iniziale: $start - Tempo Finale: $end")
        Log.d("recalculateTotals", "orderStrategy: ${orderData.orderStrategy}")
        //Log.d("recalculateTotals", "candidateOrders value: $candidateOrders")

        when (orderData.orderStrategy) {
            OrderStrategyConstants.NORMAL -> {
                Log.d("recalculateTotals", "Normal strategy selected")
                orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(start, end)
                Log.d("recalculateTotals", "Tempo impiegato calcolato: ${orderData.tempoImpiegato}")
                orderData.batchMasterOrderID = null
            }
            OrderStrategyConstants.SERIAL -> {
                Log.d("recalculateTotals", "Serial strategy selected")
                if (::candidateOrders.isInitialized && candidateOrders.isNotEmpty()) {
                    Log.d("recalculateTotals", "Candidati seriali inizializzati")
                    // 1. Verifica la serialità inversa e aggiorna relatedOrders
                    val toUpdate = mutableListOf<CandidateOrder>()
                    for (candidate in candidateOrders) {
                        if (relatedOrders[candidate.ID] == OrderStrategyConstants.SERIAL &&
                            TimeUtils.timeToMinutes(orderData.endTime) < TimeUtils.timeToMinutes(candidate.EndTime)) {
                            relatedOrders[candidate.ID] = -1
                            toUpdate.add(candidate)
                        }
                    }

                    // 2. Trova il predecessore con il massimo EndTime tra i candidati con relatedOrders = SERIAL
                    val validPredecessors = candidateOrders.filter { relatedOrders[it.ID] == OrderStrategyConstants.SERIAL }
                    val maxEndTimeCandidate = validPredecessors.maxByOrNull { TimeUtils.timeToMinutes(it.EndTime) }

                    if (maxEndTimeCandidate != null &&
                        TimeUtils.timeToMinutes(orderData.startTime) < TimeUtils.timeToMinutes(maxEndTimeCandidate.EndTime)) {
                        Log.d("recalculateTotals", "Predecessore seriale trovato: ${maxEndTimeCandidate.EndTime}")
                        orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(maxEndTimeCandidate.EndTime, orderData.endTime)
                        orderData.batchMasterOrderID = null
                    } else {
                        // Fallback: usa il tempo individuale
                        orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(orderData.startTime, orderData.endTime)
                        orderData.batchMasterOrderID = null
                    }
                } else {
                    // Nessun candidato o non inizializzato
                    Log.d("recalculateTotals", "Nessun candidato seriale disponibile o candidateOrders non inizializzato")
                    orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(orderData.startTime, orderData.endTime)
                    orderData.batchMasterOrderID = null
                }
            }
            OrderStrategyConstants.PARALLEL -> {
                Log.d("recalculateTotals", "Parallel strategy selected")
                if (::candidateOrders.isInitialized && candidateOrders.isNotEmpty()) {
                    val timeIntervals = mutableListOf(Pair(orderData.startTime, orderData.endTime))
                    candidateOrders.forEach { candidate ->
                        timeIntervals.add(Pair(candidate.StartTime, candidate.EndTime))
                    }

                    val minStartTime = timeIntervals.minByOrNull { TimeUtils.timeToMinutes(it.first) }!!.first
                    val maxEndTime = timeIntervals.maxByOrNull { TimeUtils.timeToMinutes(it.second) }!!.second
                    val parallelTime = TimeUtils.calculateTimeDifference(minStartTime, maxEndTime)
                    totalPaga = candidateOrders.sumOf { it.PagaTotale } + orderData.pagaTotale
                    orderData.tempoImpiegato = parallelTime
                    orderData.batchMasterOrderID = candidateOrders.minByOrNull {
                        TimeUtils.timeToMinutes(it.StartTime)
                    }?.ID
                } else {
                    // Nessun candidato o non inizializzato
                    Log.d("recalculateTotals", "Nessun candidato parallelo disponibile o candidateOrders non inizializzato")
                    orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(orderData.startTime, orderData.endTime)
                    orderData.batchMasterOrderID = null
                }
            }
            OrderStrategyConstants.SERIAL_PARALLEL -> {
                val parallelCandidates = if (::candidateOrders.isInitialized) {
                    candidateOrders.filter { relatedOrders[it.ID] == OrderStrategyConstants.PARALLEL }
                } else {
                    emptyList()
                }
                val serialCandidates = if (::candidateOrders.isInitialized) {
                    candidateOrders.filter { relatedOrders[it.ID] == OrderStrategyConstants.SERIAL }
                } else {
                    emptyList()
                }

                // Calcola il tempo del gruppo parallelo
                var parallelEndTime: Time?

                if (parallelCandidates.isNotEmpty()) {
                    val timeIntervals = mutableListOf(Pair(orderData.startTime, orderData.endTime))
                    parallelCandidates.forEach { candidate ->
                        timeIntervals.add(Pair(candidate.StartTime, candidate.EndTime))
                    }
                    //val minStartTime = timeIntervals.minByOrNull { TimeUtils.timeToMinutes(it.first) }!!.first
                    parallelEndTime = timeIntervals.maxByOrNull { TimeUtils.timeToMinutes(it.second) }!!.second
                    totalPaga += parallelCandidates.sumOf { it.PagaTotale }
                    orderData.batchMasterOrderID = parallelCandidates.minByOrNull {
                        TimeUtils.timeToMinutes(it.StartTime)
                    }?.ID
                } else {
                    parallelEndTime = orderData.endTime
                    orderData.batchMasterOrderID = null
                }

                // Calcola il tempo seriale
                if (serialCandidates.isNotEmpty()) {
                    // 1. Verifica la serialità inversa e aggiorna relatedOrders
                    for (candidate in serialCandidates) {
                        if (relatedOrders[candidate.ID] == OrderStrategyConstants.SERIAL &&
                            TimeUtils.timeToMinutes(orderData.endTime) < TimeUtils.timeToMinutes(candidate.EndTime)) {
                            relatedOrders[candidate.ID] = -1
                        }
                    }

                    // 2. Trova il predecessore seriale con il massimo EndTime
                    val validSerialCandidates = serialCandidates.filter { relatedOrders[it.ID] == OrderStrategyConstants.SERIAL }
                    val maxSerialEndTimeCandidate = validSerialCandidates.maxByOrNull { TimeUtils.timeToMinutes(it.EndTime) }

                    if (maxSerialEndTimeCandidate != null &&
                        TimeUtils.timeToMinutes(orderData.startTime) < TimeUtils.timeToMinutes(parallelEndTime)) {
                        orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(parallelEndTime, maxSerialEndTimeCandidate.EndTime)
                    } else {
                        // Fallback: usa il tempo seriale o parallelo
                        orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(
                            orderData.startTime,
                            maxSerialEndTimeCandidate?.EndTime ?: parallelEndTime
                        )
                    }
                } else {
                    // Nessun candidato seriale
                    orderData.tempoImpiegato = TimeUtils.calculateTimeDifference(orderData.startTime, parallelEndTime)
                }
            }
        }
        Log.d("ImageRecognitionActivity", "Tempo impiegato calcolato: ${orderData.tempoImpiegato}\nPaga Totale: $totalPaga")
        orderData.pagaOraria = if (orderData.tempoImpiegato > 0) (totalPaga * 60) / orderData.tempoImpiegato else 0.0
    }

    private fun validateOrderData(): Boolean {
        // Basic validation
        if (orderData.data.isEmpty()) return false
        if (orderData.tempoImpiegato < 0) return false
        if (orderData.numeroOrdini < 0) return false
        if (orderData.pagaBase < 0 || orderData.riscossiContanti < 0 || orderData.pagatoContantiRistorante < 0 ||
            orderData.pagaExtra < 0 || orderData.mancia < 0 || orderData.manciaContanti < 0) return false
        try {
            val start = LocalTime.parse(orderData.startTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))
            val end = LocalTime.parse(orderData.endTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))
            if (start.isAfter(end)) return false
        } catch (e: Exception) {
            Log.e("ImageRecognitionActivity", "Invalid time format in validation: ${e.message}")
            return false
        }
        return orderData.orderStrategy in 0..3
    }

    private fun saveOcrUsesLeft() {
        sharedPref.edit { putInt("ocr_uses_left", ocrUsesLeft) }
        updateButtonText()
    }

    private fun loadOcrUsesLeft() {
        ocrUsesLeft = sharedPref.getInt("ocr_uses_left", 0)
    }

    private fun startOCR(selectedUri: Uri?) {
        if (selectedUri != null) {
            fields.clear()
            adapter.notifyDataSetChanged()
            orderData = OrderData()
            try {
                val inputStream = contentResolver.openInputStream(selectedUri)
                if (inputStream != null) {
                    val fileSize = inputStream.available()
                    val maxSize = 5 * 1024 * 1024 // 5 MB
                    if (fileSize > maxSize) {
                        inputStream.close()
                        updateFields()
                        Toast.makeText(this, getString(R.string.ocr_too_large), Toast.LENGTH_LONG).show()
                        Log.d("ImageRecognitionActivity", "Immagine troppo grande: $fileSize byte")
                        return
                    }

                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    if (bitmap == null) {
                        updateFields()
                        Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                        Log.e("ImageRecognitionActivity", "Errore: Bitmap nullo")
                        return
                    }

                    imageView.setImageBitmap(bitmap)

                    OCRHelper.extractDataFromImage(
                        this,
                        bitmap,
                        selectedUri,
                        tvSelectedDate.text.toString(),
                        toggleUseDate.isChecked
                    ) { result ->
                        when (result.codice) {
                            OcrResultCode.SUCCESS -> {
                                orderData = result.orderData
                                ocrUsesLeft--
                                saveOcrUsesLeft()
                                try {
                                    val startTime = orderData.startTime.toString().substring(0, 5)
                                    val endTime = orderData.endTime.toString().substring(0, 5)
                                    candidateOrders = dbHelper.getCandidateMultiAppOrders(orderData.data, startTime, endTime)
                                    Log.d("ImageRecognitionActivity", "Candidate orders: $candidateOrders")
                                    if (candidateOrders.isNotEmpty()) {
                                        Log.d("ImageRecognitionActivity", "Showing multi-app dialog")
                                        MultiAppUtils.showMultiAppDialog(
                                            context = this,
                                            order = orderData,
                                            candidates = candidateOrders,
                                            onStrategySelected = { selectedStrategy, selectedStrategies ->
                                                orderData.orderStrategy = selectedStrategy
                                                // Set batchMasterOrderID to the earliest candidate order ID
                                                orderData.batchMasterOrderID = candidateOrders.minByOrNull { it.StartTime }?.ID
                                                relatedOrders.clear()
                                                relatedOrders.putAll(selectedStrategies)
                                                recalculateTotals()
                                                updateFields()
                                                adapter.updateSaveButtonState()
                                                Toast.makeText(this, "OCR completato e strategia aggiornata!", Toast.LENGTH_SHORT).show()
                                            },
                                            onCancel = {
                                                Log.d("ImageRecognitionActivity", "Multi-app dialog canceled")
                                                // Reset in-memory state if needed
                                                relatedOrders.clear() // Or restore previous state
                                                orderData.orderStrategy = OrderStrategyConstants.NORMAL
                                                orderData.batchMasterOrderID = null
                                                recalculateTotals()
                                                updateFields()
                                                adapter.updateSaveButtonState()
                                            }
                                        )
                                        } else {
                                        Log.d("ImageRecognitionActivity", "No candidate orders found, Order Is NORMAL")
                                        orderData.orderStrategy = OrderStrategyConstants.NORMAL
                                        relatedOrders.clear()
                                        updateFields()
                                        adapter.updateSaveButtonState()
                                        //Toast.makeText(this, "OCR completato con successo!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("ImageRecognitionActivity", "Errore nel controllo multiapp: ${e.message}")
                                    orderData.orderStrategy = OrderStrategyConstants.NORMAL
                                    relatedOrders.clear()
                                    updateFields()
                                    adapter.updateSaveButtonState()
                                    //Toast.makeText(this, "OCR completato, ma errore nel controllo multiapp", Toast.LENGTH_SHORT).show()
                                }
                                updateFields()
                                Toast.makeText(this, "OCR completato con successo!", Toast.LENGTH_SHORT).show()
                            }
                            OcrResultCode.FILE_TOO_LARGE -> {
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_too_large), Toast.LENGTH_LONG).show()
                            }
                            OcrResultCode.IMAGE_LOAD_FAILED -> {
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                            }
                            OcrResultCode.OCR_PROCESSING_FAILED -> {
                                updateFields()
                                Toast.makeText(this, getString(R.string.ocr_processing_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    updateFields()
                    Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                    Log.e("ImageRecognitionActivity", "InputStream nullo")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateFields()
                Toast.makeText(this, getString(R.string.ocr_load_failed), Toast.LENGTH_LONG).show()
                Log.e("ImageRecognitionActivity", "Errore in startOCR: ${e.message}")
            }
        }
    }

    private fun updateFields() {
        fields.clear()
        val orderStrategyOptions = listOf(
            getString(R.string.normale),
            getString(R.string.seriale),
            getString(R.string.parallelo),
            getString(R.string.seriale_parallelo)
        )

        fields.add(FieldItem(
            key = FieldKeys.DATA,
            name = getString(R.string.data),
            value = orderData.data,
            isEditable = true,
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.RISTORANTE,
            name = getString(R.string.ristorante),
            value = orderData.ristorante,
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.START_TIME,
            name = getString(R.string.start_time),
            value = orderData.startTime.toString().substring(0, 5),
            fieldType = FieldType.TIME
        ))
        fields.add(FieldItem(
            key = FieldKeys.END_TIME,
            name = getString(R.string.end_time),
            value = orderData.endTime.toString().substring(0, 5),
            fieldType = FieldType.TIME
        ))
        fields.add(FieldItem(
            key = FieldKeys.TEMPO_IMPIEGATO,
            name = getString(R.string.tempo_impiegato_ocr),
            value = orderData.tempoImpiegato.toString(),
            fieldType = FieldType.TEXT
        ))
        // Commentato per possibile riutilizzo futuro
        /*
        val isDoubleGlovoOrder = orderData.providerID == 2 && orderData.numeroOrdini > 1
        if (isDoubleGlovoOrder) {
            fields.add(FieldItem(
                key = FieldKeys.WARNING_DOUBLE_ORDER,
                name = getString(R.string.warning_double_order_time),
                value = "",
                isWarning = true,
                isEditable = false,
                fieldType = FieldType.TEXT
            ))
        }
        */
        fields.add(FieldItem(
            key = FieldKeys.NUMERO_ORDINI,
            name = getString(R.string.numero_ordini),
            value = orderData.numeroOrdini.toString(),
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.PAGA_BASE,
            name = getString(R.string.paga_base, currencySymbol),
            value = CurrencyFormatter.format(orderData.pagaBase),
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.RISCOSSI_CONTANTI,
            name = getString(R.string.riscossi_contanti, currencySymbol),
            value = CurrencyFormatter.format(orderData.riscossiContanti),
            fieldType = FieldType.TEXT
        ))
        if (orderData.providerID == 2) {
            fields.add(FieldItem(
                key = FieldKeys.PAGATO_CONTANTI_RISTORANTE,
                name = getString(R.string.pagato_contanti_ristorante, currencySymbol),
                value = CurrencyFormatter.format(orderData.pagatoContantiRistorante),
                fieldType = FieldType.TEXT
            ))
        } else {
            fields.add(FieldItem(
                key = FieldKeys.PAGA_EXTRA,
                name = getString(R.string.paga_extra, currencySymbol),
                value = CurrencyFormatter.format(orderData.pagaExtra),
                fieldType = FieldType.TEXT
            ))
        }
        fields.add(FieldItem(
            key = FieldKeys.MANCIA,
            name = getString(R.string.mancia, currencySymbol),
            value = CurrencyFormatter.format(orderData.mancia),
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.MANCIA_CONTANTI,
            name = getString(R.string.mancia_contanti, currencySymbol),
            value = CurrencyFormatter.format(orderData.manciaContanti),
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.PAGA_TOTALE,
            name = getString(R.string.paga_totale, currencySymbol),
            value = CurrencyFormatter.format(orderData.pagaTotale),
            isEditable = false,
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.PAGA_ORARIA,
            name = getString(R.string.paga_oraria, currencySymbol),
            value = CurrencyFormatter.format(orderData.pagaOraria),
            isEditable = false,
            fieldType = FieldType.TEXT
        ))
        fields.add(FieldItem(
            key = FieldKeys.ORDER_STRATEGY,
            name = getString(R.string.order_strategy),
            value = orderData.orderStrategy.toString(),
            fieldType = FieldType.DROPDOWN,
            dropdownOptions = orderStrategyOptions
        ))

        adapter.notifyDataSetChanged()
    }
}