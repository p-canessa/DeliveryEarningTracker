package com.piero.deliveryearningtracker

import android.text.TextWatcher
import android.text.Editable
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.sql.Time
import java.time.format.DateTimeParseException

class OrderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var orderId: Long? = null
    private val dbHelper = DatabaseHelper(context)
    private val decimalFormat = DecimalFormat("0.00")
    private val calendar = Calendar.getInstance()
    private var onOrderSavedListener: (() -> Unit)? = null
    private var onOrderDeletedListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.order_item_layout, this, true)
        setOnClickListener { showEditDialog() }
        setOnLongClickListener { showCustomContextMenu() }
    }

    fun setOrderData(id: Long) {
        this.visibility = View.VISIBLE
        orderId = id
        val ordine = dbHelper.getOrdineById(id)
        if (ordine != null) {
            findViewById<ImageView>(R.id.seriale_icon).visibility = if (ordine.orderStrategy and OrderStrategyConstants.SERIAL != 0) View.VISIBLE else View.GONE
            findViewById<ImageView>(R.id.parallelo_icon).visibility = if (ordine.orderStrategy and OrderStrategyConstants.PARALLEL != 0) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.numero_ordini).text = context.getString(R.string.orders, ordine.numeroOrdini)
            findViewById<TextView>(R.id.paga_totale).text = context.getString(R.string.total, CurrencyFormatter.format(ordine.pagaTotale))
            findViewById<ImageView>(R.id.mancia_icon).visibility = if (ordine.mancia > 0) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.tempo_impiegato).text = context.getString(R.string.time_spent, ordine.tempoImpiegato)
            findViewById<TextView>(R.id.paga_oraria).text = context.getString(R.string.hourly_rate, CurrencyFormatter.format(ordine.pagaOraria))
        }
    }

    fun setOnOrderSavedListener(listener: () -> Unit) {
        onOrderSavedListener = listener
    }

    fun setOnOrderDeletedListener(listener: () -> Unit) {
        onOrderDeletedListener = listener
    }

    private fun showCustomContextMenu(): Boolean {
        val popup = PopupMenu(context, this)
        popup.menuInflater.inflate(R.menu.order_context_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete) {
                showDeleteConfirmationDialog()
                true
            } else {
                false
            }
        }
        popup.show()
        return true
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.delete_confirmation_title))
            .setMessage(context.getString(R.string.delete_confirmation_message))
            .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                orderId?.let { id ->
                    dbHelper.deleteOrdine(id)
                    onOrderDeletedListener?.invoke()
                }
            }
            .setNegativeButton(context.getString(R.string.no), null)
            .show()
    }

    private fun validateTimeInput(input: String): Boolean {
        if (input.isEmpty()) return false
        val timePattern = Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
        return try {
            if (!timePattern.matches(input)) return false
            LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm"))
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private fun showTimePicker(editText: EditText, currentTime: String?) {
        val timeParts = currentTime?.split(":")?.map { it.toIntOrNull() ?: 0 } ?: listOf(0, 0)
        val hour = timeParts.getOrElse(0) { 0 }
        val minute = timeParts.getOrElse(1) { 0 }
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                editText.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            },
            hour, minute, true
        ).show()
    }

    fun showEditDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.order_edit_dialog, null)
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (orderId == null) context.getString(R.string.new_order) else context.getString(R.string.edit_order))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        val dataText = dialogView.findViewById<TextView>(R.id.data_text)
        val providerSpinner = dialogView.findViewById<Spinner>(R.id.provider_spinner)
        val pagaBaseEdit = dialogView.findViewById<EditText>(R.id.paga_base_edit)
        val pagaExtraEdit = dialogView.findViewById<EditText>(R.id.paga_extra_edit)
        val manciaEdit = dialogView.findViewById<EditText>(R.id.mancia_edit)
        val manciaContantiEdit = dialogView.findViewById<EditText>(R.id.mancia_contanti_edit)
        val riscossiContantiEdit = dialogView.findViewById<EditText>(R.id.riscossi_contanti_edit)
        val numeroOrdiniEdit = dialogView.findViewById<EditText>(R.id.numero_ordini_edit)
        val tempoImpiegatoEdit = dialogView.findViewById<EditText>(R.id.tempo_impiegato_edit)
        val pagaTotaleText = dialogView.findViewById<TextView>(R.id.paga_totale_text)
        val ristoranteEdit = dialogView.findViewById<EditText>(R.id.ristorante_edit)
        val startTimeEdit = dialogView.findViewById<EditText>(R.id.start_time_edit)
        val endTimeEdit = dialogView.findViewById<EditText>(R.id.end_time_edit)
        val strategyText = dialogView.findViewById<TextView>(R.id.strategy_text) // Assumi aggiunto in XML come <TextView android:id="@+id/strategy_text" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Strategy: Normal" />

        var initialPagaTotale: Double? = null
        val currencySymbol = CurrencyFormatter.getCurrencySymbol()

        // Imposta etichette con simbolo valuta
        dialogView.findViewById<TextView>(R.id.paga_base_label).text = resources.getString(R.string.paga_base, currencySymbol)
        dialogView.findViewById<TextView>(R.id.paga_extra_label).text = resources.getString(R.string.paga_extra, currencySymbol)
        dialogView.findViewById<TextView>(R.id.mancia_label).text = resources.getString(R.string.mancia, currencySymbol)
        dialogView.findViewById<TextView>(R.id.mancia_contanti_label).text = resources.getString(R.string.mancia_contanti, currencySymbol)
        dialogView.findViewById<TextView>(R.id.riscossi_contanti_label).text = resources.getString(R.string.riscossi_contanti, currencySymbol)
        dialogView.findViewById<TextView>(R.id.paga_totale_label).text = resources.getString(R.string.paga_totale, currencySymbol)

        // Popola lo Spinner con i provider
        val providers = dbHelper.getProviders()
        val providerNames = providers.map { it.second }
        val providerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, providerNames)
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        providerSpinner.adapter = providerAdapter

        // Imposta valori iniziali
        var oldStrategy = OrderStrategyConstants.NORMAL
        var oldBatchMasterId: Long? = null
        if (orderId != null) {
            val ordine = dbHelper.getOrdineById(orderId!!)
            if (ordine != null) {
                dataText.text = ordine.data
                val providerIndex = providers.indexOfFirst { it.first == ordine.providerID }
                if (providerIndex >= 0) {
                    providerSpinner.setSelection(providerIndex)
                }
                val pagaBaseLorda = ordine.pagaBase + ordine.riscossiContanti
                val manciaInApp = ordine.mancia - ordine.manciaContanti
                pagaBaseEdit.setText(if (pagaBaseLorda != 0.0) decimalFormat.format(pagaBaseLorda) else "")
                pagaExtraEdit.setText(if (ordine.pagaExtra != 0.0) decimalFormat.format(ordine.pagaExtra) else "")
                manciaEdit.setText(if (manciaInApp != 0.0) decimalFormat.format(manciaInApp) else "")
                manciaContantiEdit.setText(if (ordine.manciaContanti != 0.0) decimalFormat.format(ordine.manciaContanti) else "")
                riscossiContantiEdit.setText(if (ordine.riscossiContanti != 0.0) decimalFormat.format(ordine.riscossiContanti) else "")
                numeroOrdiniEdit.setText(if (ordine.numeroOrdini != 0) ordine.numeroOrdini.toString() else "")
                tempoImpiegatoEdit.setText(if (ordine.tempoImpiegato != 0) ordine.tempoImpiegato.toString() else "")
                initialPagaTotale = ordine.pagaTotale
                ristoranteEdit.setText(ordine.ristorante)
                startTimeEdit.setText(ordine.startTime.toString().substring(0, 5))
                endTimeEdit.setText(ordine.endTime.toString().substring(0, 5))
                oldStrategy = ordine.orderStrategy
                oldBatchMasterId = ordine.batchMasterOrderID
                strategyText.text = getStrategyDisplayString(ordine.orderStrategy) // Funzione helper per convertire int in string leggibile
            }
        } else {
            dataText.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            providerSpinner.setSelection(0)
            pagaBaseEdit.setText("")
            pagaExtraEdit.setText("")
            manciaEdit.setText("")
            manciaContantiEdit.setText("")
            riscossiContantiEdit.setText("")
            numeroOrdiniEdit.setText("")
            tempoImpiegatoEdit.setText("")
            ristoranteEdit.setText("")
            startTimeEdit.setText("")
            endTimeEdit.setText("")
            strategyText.text = getStrategyDisplayString(OrderStrategyConstants.NORMAL)
        }

        // Abilita TimePickerDialog per startTime e endTime
        startTimeEdit.setOnClickListener { showTimePicker(startTimeEdit, startTimeEdit.text.toString()) }
        endTimeEdit.setOnClickListener { showTimePicker(endTimeEdit, endTimeEdit.text.toString()) }

        // Aggiorna paga totale in tempo reale
        val updateTotal = {
            val pagaBase = parseCurrencyInput(pagaBaseEdit.text.toString()) ?: 0.0
            val pagaExtra = parseCurrencyInput(pagaExtraEdit.text.toString()) ?: 0.0
            val manciaInApp = parseCurrencyInput(manciaEdit.text.toString()) ?: 0.0
            val manciaContanti = parseCurrencyInput(manciaContantiEdit.text.toString()) ?: 0.0
            val pagaTotale = pagaBase + pagaExtra + manciaInApp + manciaContanti
            pagaTotaleText.text = CurrencyFormatter.format(pagaTotale)
        }

        pagaBaseEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateTotal()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        pagaExtraEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateTotal()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        manciaEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateTotal()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        manciaContantiEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateTotal()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        if (orderId != null && initialPagaTotale != null) {
            pagaTotaleText.text = CurrencyFormatter.format(initialPagaTotale)
        } else {
            updateTotal()
        }

        // DatePicker per data
        dataText.setOnClickListener {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    dataText.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Validazione orari
                val startTime = startTimeEdit.text.toString()
                val endTime = endTimeEdit.text.toString()

                if (!validateTimeInput(startTime)) {
                    startTimeEdit.error = "Formato ora non valido (HH:mm)"
                    return@setOnClickListener
                }
                if (!validateTimeInput(endTime)) {
                    endTimeEdit.error = "Formato ora non valido (HH:mm)"
                    return@setOnClickListener
                }

                // Opzionale: verifica che endTime sia successivo a startTime
                try {
                    val start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"))
                    if (end.isBefore(start) || end == start) {
                        endTimeEdit.error = "L'ora di fine deve essere successiva all'ora di inizio"
                        return@setOnClickListener
                    }
                } catch (e: DateTimeParseException) {
                    Log.e("OrderItemView", "Errore parsing orari: ${e.message}")
                    Toast.makeText(context, "Errore nel formato degli orari", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedProviderIndex = providerSpinner.selectedItemPosition
                val providerId = providers[selectedProviderIndex].first

                val data = dataText.text.toString()
                val pagaBaseBruta = parseCurrencyInput(pagaBaseEdit.text.toString()) ?: 0.0
                val pagaExtra = parseCurrencyInput(pagaExtraEdit.text.toString()) ?: 0.0
                val manciaInApp = parseCurrencyInput(manciaEdit.text.toString()) ?: 0.0
                val manciaContanti = parseCurrencyInput(manciaContantiEdit.text.toString()) ?: 0.0
                val riscossiContanti = parseCurrencyInput(riscossiContantiEdit.text.toString()) ?: 0.0
                val numeroOrdini = numeroOrdiniEdit.text.toString().toIntOrNull() ?: 0
                val tempoImpiegato = parseTimeInput(tempoImpiegatoEdit.text.toString()) ?: 0
                val ristorante = ristoranteEdit.text.toString()

                // Crea ordine temporaneo con strategia old o default
                val tempOrder = createTempOrderData(
                    data, providerId, pagaBaseBruta, pagaExtra, manciaInApp, manciaContanti, riscossiContanti,
                    numeroOrdini, tempoImpiegato, ristorante, startTime, endTime, oldStrategy, oldBatchMasterId
                )

                // Gestisci salvataggio con controlli
                handleSaveWithStrategyCheck(tempOrder, dialog)
            }
        }
        dialog.show()
    }

    private fun createTempOrderData(
        data: String,
        providerId: Int,
        pagaBaseBruta: Double,
        pagaExtra: Double,
        manciaInApp: Double,
        manciaContanti: Double,
        riscossiContanti: Double,
        numeroOrdini: Int,
        tempoImpiegato: Int,
        ristorante: String,
        startTime: String,
        endTime: String,
        strategy: Int,
        batchMasterId: Long?
    ): OrderData {
        val manciaTotale = manciaInApp + manciaContanti
        val pagaBaseNeta = if (providerId == 2) {
            pagaBaseBruta
        } else {
            pagaBaseBruta - riscossiContanti
        }
        val pagaTotale = pagaBaseNeta + pagaExtra + manciaTotale
        val pagaOraria = if (tempoImpiegato > 0) pagaTotale / (tempoImpiegato / 60.0) else 0.0

        return OrderData(
            id = orderId ?: -1L,
            data = data,
            providerID = providerId,
            pagaBase = pagaBaseNeta,
            pagaExtra = pagaExtra,
            mancia = manciaTotale,
            manciaContanti = manciaContanti,
            riscossiContanti = riscossiContanti,
            numeroOrdini = numeroOrdini,
            tempoImpiegato = tempoImpiegato,
            pagaTotale = pagaTotale,
            pagaOraria = pagaOraria,
            ristorante = ristorante,
            startTime = java.sql.Time.valueOf(LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm")).format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
            endTime = java.sql.Time.valueOf(LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm")).format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
            orderStrategy = strategy,
            batchMasterOrderID = batchMasterId
        )
    }

    private fun handleSaveWithStrategyCheck(tempOrder: OrderData, dialog: AlertDialog) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Cancella l'ordine esistente, se in modifica
            if (tempOrder.id != -1L) {
                dbHelper.deleteOrdine(tempOrder.id) // Usa la logica di cancellazione multi-app
                tempOrder.id = -1L // Forza nuovo ID per l'insert
            }

            // Controlla candidati multi-app
            val candidates = dbHelper.getCandidateMultiAppOrders(
                tempOrder.data,
                tempOrder.startTime.toString().substring(0, 5),
                tempOrder.endTime.toString().substring(0, 5)
            )

            if (candidates.isNotEmpty()) {
                // Mostra dialog per selezionare strategia
                MultiAppUtils.showMultiAppDialog(context, tempOrder, candidates, dbHelper) { selectedStrategy, selectedStrategies ->
                    tempOrder.orderStrategy = selectedStrategy
                    tempOrder.batchMasterOrderID = selectedStrategies.keys.firstOrNull()
                    saveOrderToDb(tempOrder)
                    db.setTransactionSuccessful()
                    onOrderSavedListener?.invoke()
                    dialog.dismiss()
                }
            } else {
                // Nessun candidato, salva come ordine normale
                tempOrder.orderStrategy = OrderStrategyConstants.NORMAL
                tempOrder.batchMasterOrderID = null
                saveOrderToDb(tempOrder)
                db.setTransactionSuccessful()
                onOrderSavedListener?.invoke()
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Log.e("OrderItemView", "Errore durante salvataggio: ${e.message}", e)
            Toast.makeText(context, "Errore salvataggio ordine", Toast.LENGTH_SHORT).show()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveOrderToDb(ordine: OrderData) {
        val newId = dbHelper.saveOrdine(ordine)
        if (newId > 0) {
            orderId = newId
            setOrderData(newId)
        } else {
            Log.e("OrderItemView", "Failed to save order")
        }
    }

    private fun getStrategyDisplayString(strategy: Int): String {
        return when (strategy) {
            OrderStrategyConstants.NORMAL -> "Normale"
            OrderStrategyConstants.SERIAL -> "Seriale"
            OrderStrategyConstants.PARALLEL -> "Parallelo"
            OrderStrategyConstants.SERIAL_PARALLEL -> "Seriale a Parallelo"
            else -> "Sconosciuto"
        }
    }

    private fun parseTimeInput(input: String): Int? {
        input.trim().let { trimmedInput ->
            if (trimmedInput.matches("\\d+".toRegex())) {
                return trimmedInput.toIntOrNull()
            }
            if (trimmedInput.matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}".toRegex())) {
                val (startStr, endStr) = trimmedInput.split("-")
                val (startHour, startMinute) = startStr.split(":").map { it.toInt() }
                val (endHour, endMinute) = endStr.split(":").map { it.toInt() }

                if (startHour !in 0..23 || startMinute !in 0..59 ||
                    endHour !in 0..23 || endMinute !in 0..59) {
                    return null
                }

                val startCalendar = Calendar.getInstance().apply {
                    set(0, 0, 0, startHour, startMinute)
                }
                val endCalendar = Calendar.getInstance().apply {
                    set(0, 0, 0, endHour, endMinute)
                }

                val diffMillis = if (endCalendar.timeInMillis < startCalendar.timeInMillis) {
                    (endCalendar.timeInMillis + 24 * 60 * 60 * 1000) - startCalendar.timeInMillis
                } else {
                    endCalendar.timeInMillis - startCalendar.timeInMillis
                }

                return (diffMillis / (1000 * 60)).toInt()
            }
        }
        return null
    }

    private fun parseCurrencyInput(input: String): Double? {
        input.trim().let { trimmedInput ->
            if (trimmedInput.isEmpty()) return null
            val normalizedInput = trimmedInput.replace(",", ".")
            return normalizedInput.toDoubleOrNull()
        }
    }
}