package com.piero.deliveryearningtracker

import android.app.DatePickerDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*


class DateRangeSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // Variabili per gestire la data di riferimento e il tipo di intervallo
    private var referenceDate: Calendar = Calendar.getInstance()
    private var intervalType: IntervalType = IntervalType.DAY
    private var textView: TextView
    private var decrementButton: ImageButton
    private var incrementButton: ImageButton
    private var spinner: Spinner
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var onChangeListener: OnChangeListener? = null
    interface OnChangeListener {
        fun onChange()
    }

    // Enum per i tipi di intervallo
    enum class IntervalType {
        DAY, WEEK, MONTH
    }

    init {
        // Infla il layout della vista personalizzata
        LayoutInflater.from(context).inflate(R.layout.date_range_selector, this, true)

        // Trova i componenti dell'interfaccia
        textView = findViewById(R.id.text_view)
        decrementButton = findViewById(R.id.decrement_button)
        incrementButton = findViewById(R.id.increment_button)
        spinner = findViewById(R.id.interval_spinner)

        val intervalTypes = resources.getStringArray(R.array.interval_types)
        // Configura la tendina (Spinner)
        val adapter = SpinnerAdapter(
            context, // Assicurati che 'context' sia il context corretto
            R.layout.spinner_item,
            intervalTypes.toList()
        )
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                intervalType = when (position) {
                    0 -> IntervalType.DAY
                    1 -> IntervalType.WEEK
                    2 -> IntervalType.MONTH
                    else -> IntervalType.DAY
                }
                updateTextView()
                onChangeListener?.onChange() // Notifica il cambiamento
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Configura i bottoni
        decrementButton.setOnClickListener { decrement() }
        incrementButton.setOnClickListener { increment() }

        // Configura il TextView per mostrare il DatePicker
        textView.setOnClickListener { showDatePicker() }

        // Inizializza la data di riferimento a oggi e l'intervallo a "giorno"
        referenceDate = Calendar.getInstance()
        intervalType = IntervalType.DAY
        updateTextView()
    }

    // Mostra il DatePicker per selezionare una nuova data
    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, day ->
                referenceDate.set(year, month, day)
                updateTextView()
                onChangeListener?.onChange() // Notifica il cambiamento
            },
            referenceDate.get(Calendar.YEAR),
            referenceDate.get(Calendar.MONTH),
            referenceDate.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Incrementa l'intervallo in base al tipo selezionato
    private fun increment() {
        when (intervalType) {
            IntervalType.DAY -> referenceDate.add(Calendar.DAY_OF_MONTH, 1)
            IntervalType.WEEK -> referenceDate.add(Calendar.WEEK_OF_YEAR, 1)
            IntervalType.MONTH -> referenceDate.add(Calendar.MONTH, 1)
        }
        updateTextView()
        onChangeListener?.onChange() // Notifica il cambiamento
    }

    // Decrementa l'intervallo in base al tipo selezionato
    private fun decrement() {
        when (intervalType) {
            IntervalType.DAY -> referenceDate.add(Calendar.DAY_OF_MONTH, -1)
            IntervalType.WEEK -> referenceDate.add(Calendar.WEEK_OF_YEAR, -1)
            IntervalType.MONTH -> referenceDate.add(Calendar.MONTH, -1)
        }
        updateTextView()
        onChangeListener?.onChange() // Notifica il cambiamento
    }

    // Aggiorna il testo visualizzato nel TextView
    private fun updateTextView() {
        val (start, end) = calculateStartEndDates()
        val displayText = when (intervalType) {
            IntervalType.DAY -> start
            IntervalType.WEEK -> "$start to $end"
            IntervalType.MONTH -> "$start to $end"
        }
        textView.text = displayText
    }

    // Calcola le date di inizio e fine in base al tipo di intervallo
    private fun calculateStartEndDates(): Pair<String, String> {
        val cal = referenceDate.clone() as Calendar
        when (intervalType) {
            IntervalType.DAY -> {
                val dateStr = formatDate(cal.time)
                return Pair(dateStr, dateStr)
            }
            IntervalType.WEEK -> {
                // Imposta il lunedì come primo giorno della settimana
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val startDate = formatDate(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val endDate = formatDate(cal.time)
                return Pair(startDate, endDate)
            }
            IntervalType.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = formatDate(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val endDate = formatDate(cal.time)
                return Pair(startDate, endDate)
            }
        }
    }

    // Formatta la data nel formato "yyyy-MM-dd"
    private fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }

    // Restituisce la clausola SQL per l'intervallo selezionato
    fun getSqlClause(dateColumn: String): String {
        val (start, end) = calculateStartEndDates()
        return "WHERE $dateColumn BETWEEN '$start' AND '$end'"
    }

    fun setOnChangeListener(listener: OnChangeListener) {
        onChangeListener = listener
    }
}