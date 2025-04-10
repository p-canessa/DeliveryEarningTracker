package com.piero.deliveryearningtracker

import android.text.TextWatcher
import android.text.Editable
import android.app.DatePickerDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

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
            /*val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            val currencySymbol = sharedPref.getString("currency_symbol", "€") ?: "€"
            val strformatcurrency = if (currencySymbol == "€") "%1\$s %2\$s" else "%2\$s%1\$s"*/

            findViewById<TextView>(R.id.numero_ordini).text = context.getString(R.string.orders, ordine.numeroOrdini)
            findViewById<TextView>(R.id.paga_totale).text = context.getString(R.string.total,CurrencyFormatter.format(ordine.pagaTotale))
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

    fun showEditDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.order_edit_dialog, null)
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (orderId == null) context.getString(R.string.new_order) else context.getString(R.string.edit_order))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        val dataText = dialogView.findViewById<TextView>(R.id.data_text)
        val pagaBaseEdit = dialogView.findViewById<EditText>(R.id.paga_base_edit)
        val pagaExtraEdit = dialogView.findViewById<EditText>(R.id.paga_extra_edit)
        val manciaEdit = dialogView.findViewById<EditText>(R.id.mancia_edit)
        val manciaContantiEdit = dialogView.findViewById<EditText>(R.id.mancia_contanti_edit)
        val riscossiContantiEdit = dialogView.findViewById<EditText>(R.id.riscossi_contanti_edit)
        val numeroOrdiniEdit = dialogView.findViewById<EditText>(R.id.numero_ordini_edit)
        val tempoImpiegatoEdit = dialogView.findViewById<EditText>(R.id.tempo_impiegato_edit)
        val pagaTotaleText = dialogView.findViewById<TextView>(R.id.paga_totale_text)

        var initialPagaTotale: Double? = null

        val currencySymbol = CurrencyFormatter.getCurrencySymbol()

        val pagaBaselabel = dialogView.findViewById<TextView>(R.id.paga_base_label)
        pagaBaselabel.text=resources.getString(R.string.paga_base,currencySymbol)
        val pagaExtraLabel =dialogView.findViewById<TextView>(R.id.paga_extra_label)
        pagaExtraLabel.text=resources.getString(R.string.paga_extra,currencySymbol)
        val manciaLabel =dialogView.findViewById<TextView>(R.id.mancia_label)
        manciaLabel.text=resources.getString(R.string.mancia,currencySymbol)
        val manciaContantiLabel = dialogView.findViewById<TextView>(R.id.mancia_contanti_label)
        manciaContantiLabel.text = resources.getString(R.string.mancia_contanti, currencySymbol)
        val riscossiContantLabel =dialogView.findViewById<TextView>(R.id.riscossi_contanti_label)
        riscossiContantLabel.text=resources.getString(R.string.riscossi_contanti,currencySymbol)
        val pagaTotaleLabel =dialogView.findViewById<TextView>(R.id.paga_totale_label)
        pagaTotaleLabel.text=resources.getString(R.string.paga_totale,currencySymbol)


        if (orderId != null) {
            val ordine = dbHelper.getOrdineById(orderId!!)
            if (ordine != null) {
                dataText.text = ordine.data
                val pagaBaseLorda = ordine.pagaBase + ordine.riscossiContanti // Ricostruisci la paga base lorda
                val manciaInApp = ordine.mancia - ordine.manciaContanti      // Ricostruisci la mancia in app

                pagaBaseEdit.setText(if (pagaBaseLorda != 0.0) decimalFormat.format(pagaBaseLorda) else "")
                pagaExtraEdit.setText(if (ordine.pagaExtra != 0.0) decimalFormat.format(ordine.pagaExtra) else "")
                manciaEdit.setText(if (manciaInApp != 0.0) decimalFormat.format(manciaInApp) else "")
                manciaContantiEdit.setText(if (ordine.manciaContanti != 0.0) decimalFormat.format(ordine.manciaContanti) else "")
                riscossiContantiEdit.setText(if (ordine.riscossiContanti != 0.0) decimalFormat.format(ordine.riscossiContanti) else "")
                numeroOrdiniEdit.setText(if (ordine.numeroOrdini != 0) ordine.numeroOrdini.toString() else "")
                tempoImpiegatoEdit.setText(if (ordine.tempoImpiegato != 0) ordine.tempoImpiegato.toString() else "")
                initialPagaTotale = ordine.pagaTotale
            }
        } else {
            dataText.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            pagaBaseEdit.setText("")
            pagaExtraEdit.setText("")
            manciaEdit.setText("")
            manciaContantiEdit.setText("")
            riscossiContantiEdit.setText("")
            numeroOrdiniEdit.setText("")
            tempoImpiegatoEdit.setText("")
        }

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
                saveOrder(
                    dataText.text.toString(),
                    parseCurrencyInput(pagaBaseEdit.text.toString()) ?: 0.0,
                    parseCurrencyInput(pagaExtraEdit.text.toString()) ?: 0.0,
                    parseCurrencyInput(manciaEdit.text.toString()) ?: 0.0,
                    parseCurrencyInput(manciaContantiEdit.text.toString()) ?: 0.0,
                    parseCurrencyInput(riscossiContantiEdit.text.toString()) ?: 0.0,
                    numeroOrdiniEdit.text.toString().toIntOrNull() ?: 0,
                    parseTimeInput(tempoImpiegatoEdit.text.toString()) ?: 0
                )
                onOrderSavedListener?.invoke()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveOrder(
        data: String,
        pagaBaseBruta: Double,
        pagaExtra: Double,
        manciaInApp: Double,
        manciaContanti: Double,
        riscossiContanti: Double,
        numeroOrdini: Int,
        tempoImpiegato: Int
    ) {
        val manciaTotale = manciaInApp + manciaContanti
        val pagaBaseNeta = pagaBaseBruta - riscossiContanti
        val pagaTotale = pagaBaseNeta + pagaExtra + manciaTotale
        val pagaOraria = if (tempoImpiegato > 0) pagaTotale / (tempoImpiegato / 60.0) else 0.0

        val ordine = Ordine(
            id = orderId ?: -1L, // -1 per nuovi ordini
            data = data,
            pagaBase = pagaBaseNeta,
            pagaExtra = pagaExtra,
            mancia = manciaTotale,
            manciaContanti = manciaContanti,
            riscossiContanti = riscossiContanti,
            numeroOrdini = numeroOrdini,
            tempoImpiegato = tempoImpiegato,
            pagaTotale = pagaTotale,
            pagaOraria = pagaOraria
        )

        orderId = dbHelper.saveOrdine(ordine)
        setOrderData(orderId!!)
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