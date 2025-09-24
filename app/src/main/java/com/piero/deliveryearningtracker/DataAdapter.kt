package com.piero.deliveryearningtracker

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class FieldItem(
    val key: String, // Unique identifier for the field
    val name: String,
    var value: String = "",
    val isEditable: Boolean = true, // Replaces isDate, as date is handled by fieldType
    val isWarning: Boolean = false,
    val fieldType: FieldType = FieldType.TEXT,
    val dropdownOptions: List<String>? = null
)

enum class FieldType {
    TEXT, TIME, DROPDOWN
}

object FieldKeys {
    const val DATA = "data"
    const val TEMPO_IMPIEGATO = "tempo_impiegato"
    const val WARNING_DOUBLE_ORDER = "warning_double_order"
    const val NUMERO_ORDINI = "numero_ordini"
    const val PAGA_BASE = "paga_base"
    const val RISCOSSI_CONTANTI = "riscossi_contanti"
    const val PAGA_EXTRA = "paga_extra"
    const val PAGATO_CONTANTI_RISTORANTE = "pagato_contanti_ristorante"
    const val MANCIA = "mancia"
    const val MANCIA_CONTANTI = "mancia_contanti"
    const val PAGA_TOTALE = "paga_totale"
    const val PAGA_ORARIA = "paga_oraria"
    const val START_TIME = "start_time"
    const val END_TIME = "end_time"
    const val ORDER_STRATEGY = "order_strategy"
    const val RISTORANTE = "ristorante"
}

class DataAdapter(
    private val fields: MutableList<FieldItem>,
    private val onValueChanged: (String, String) -> Unit, // Changed to use key
    private val onValidationChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fieldName: TextView = itemView.findViewById(R.id.field_name)
        val fieldValue: TextView = itemView.findViewById(R.id.field_value)
        val fieldEdit: EditText = itemView.findViewById(R.id.field_edit)
        //val spinner: Spinner = itemView.findViewById(R.id.field_spinner)
        val editButton: ImageButton = itemView.findViewById(R.id.edit_button)
        val confirmButton: ImageButton = itemView.findViewById(R.id.confirm_button)
        val cancelButton: ImageButton = itemView.findViewById(R.id.cancel_edit_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_data_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val field = fields[position]
        val context = holder.itemView.context

        // Handle warning-only field (e.g., WARNING_DOUBLE_ORDER)
        if (field.isWarning && field.value.isEmpty()) {
            holder.fieldName.text = field.name
            holder.fieldName.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            holder.fieldValue.visibility = View.GONE
            holder.fieldEdit.visibility = View.GONE
            //holder.spinner.visibility = View.GONE
            holder.editButton.visibility = View.GONE
            holder.confirmButton.visibility = View.GONE
            holder.cancelButton.visibility = View.GONE
            updateSaveButtonState()
            return
        }

        // Determine if field is invalid
        val isInvalid = when (field.key) {
            FieldKeys.TEMPO_IMPIEGATO, FieldKeys.NUMERO_ORDINI, FieldKeys.PAGA_BASE,
            FieldKeys.PAGA_TOTALE, FieldKeys.PAGA_ORARIA -> field.value == "0"
            FieldKeys.START_TIME, FieldKeys.END_TIME -> !field.value.matches(Regex("\\d{2}:\\d{2}"))
            FieldKeys.ORDER_STRATEGY -> field.value.toIntOrNull() !in 0..3
            else -> false
        } || field.isWarning

        val textColor = ContextCompat.getColor(
            context,
            if (isInvalid) android.R.color.holo_red_dark else R.color.text_color
        )
        holder.fieldName.setTextColor(textColor)
        holder.fieldValue.setTextColor(textColor)
        if (isInvalid) {
            holder.editButton.setColorFilter(
                ContextCompat.getColor(context, android.R.color.holo_red_dark),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            holder.editButton.clearColorFilter()
        }

        holder.fieldName.text = field.name
        holder.fieldValue.text = field.value

        // Configure UI based on field type
        when (field.fieldType) {
            FieldType.TEXT -> {
                holder.fieldValue.visibility = View.VISIBLE
                holder.fieldEdit.visibility = View.GONE
                //holder.spinner.visibility = View.GONE
                if (!field.isEditable) {
                    holder.editButton.visibility = View.GONE
                } else {
                    holder.editButton.visibility = View.VISIBLE
                }
            }
            FieldType.TIME -> {
                holder.fieldValue.visibility = View.VISIBLE
                holder.fieldEdit.visibility = View.GONE
                //holder.spinner.visibility = View.GONE
                holder.editButton.visibility = View.VISIBLE
            }
            FieldType.DROPDOWN -> {
                holder.fieldValue.visibility = View.VISIBLE
                holder.fieldEdit.visibility = View.GONE
                //holder.spinner.visibility = View.GONE
                if (field.isEditable) {
                    holder.editButton.visibility = View.VISIBLE
                }
                holder.confirmButton.visibility = View.GONE
                holder.cancelButton.visibility = View.GONE

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    field.dropdownOptions ?: emptyList()
                )
                //holder.spinner.adapter = adapter
                val currentValue = field.value.toIntOrNull() ?: 0
                //holder.spinner.setSelection(currentValue)
                holder.fieldValue.text = when (currentValue) {
                    OrderStrategyConstants.NORMAL -> field.dropdownOptions?.get(0) ?: "Normale"
                    OrderStrategyConstants.SERIAL -> field.dropdownOptions?.get(1) ?: "Seriale"
                    OrderStrategyConstants.PARALLEL -> field.dropdownOptions?.get(2) ?: "Parallelo"
                    OrderStrategyConstants.SERIAL_PARALLEL -> "${field.dropdownOptions?.get(1) ?: "Seriale"}, ${field.dropdownOptions?.get(2) ?: "Parallelo"}"
                    else -> field.value
                }
            }
        }

        holder.editButton.setOnClickListener {
            when (field.fieldType) {
                FieldType.TEXT -> {
                    holder.fieldValue.visibility = View.GONE
                    holder.fieldEdit.visibility = View.VISIBLE
                    holder.fieldEdit.setText(field.value)
                    holder.fieldEdit.selectAll()
                    holder.fieldEdit.requestFocus()
                    showKeyboard(holder.fieldEdit)
                    holder.editButton.visibility = View.GONE
                    holder.confirmButton.visibility = View.VISIBLE
                    holder.cancelButton.visibility = View.VISIBLE
                }
                FieldType.TIME -> {
                    holder.fieldValue.visibility = View.GONE
                    holder.fieldEdit.visibility = View.VISIBLE
                    holder.fieldEdit.setText(field.value)
                    holder.fieldEdit.isEnabled = false // Prevent manual typing
                    showTimePickerDialog(context) { time ->
                        holder.fieldEdit.setText(time)
                        // Don't call onValueChanged yet, wait for confirm
                    }
                    holder.editButton.visibility = View.GONE
                    holder.confirmButton.visibility = View.VISIBLE
                    holder.cancelButton.visibility = View.VISIBLE
                }
                FieldType.DROPDOWN -> {
                    val popupMenu = PopupMenu(context, holder.editButton)
                    field.dropdownOptions?.forEachIndexed { index, option ->
                        popupMenu.menu.add(0, index, index, option)
                    }
                    popupMenu.setOnMenuItemClickListener { item ->
                        holder.fieldValue.tag = item.itemId.toString() // Salva il valore temporaneo in tag
                        holder.fieldValue.text = when (item.itemId) {
                            OrderStrategyConstants.NORMAL -> field.dropdownOptions?.get(0) ?: "Normale"
                            OrderStrategyConstants.SERIAL -> field.dropdownOptions?.get(1) ?: "Seriale"
                            OrderStrategyConstants.PARALLEL -> field.dropdownOptions?.get(2) ?: "Parallelo"
                            OrderStrategyConstants.SERIAL_PARALLEL -> "${field.dropdownOptions?.get(1) ?: "Seriale"}, ${field.dropdownOptions?.get(2) ?: "Parallelo"}"
                            else -> item.title.toString()
                        }
                        holder.fieldValue.visibility = View.VISIBLE
                        holder.editButton.visibility = View.GONE
                        holder.confirmButton.visibility = View.VISIBLE
                        holder.cancelButton.visibility = View.VISIBLE
                        true
                    }
                    popupMenu.show()
                }
            }
        }

        holder.confirmButton.setOnClickListener {
            val newValue = when (field.fieldType) {
                FieldType.DROPDOWN -> holder.fieldValue.tag?.toString() ?: fields[position].value
                else -> holder.fieldEdit.text.toString().replace(",", ".")
            }
            Log.d("DataAdapter", "${field.name}.${field.value} <- $newValue")
            fields[position].value = newValue
            onValueChanged(field.key, newValue)
            holder.fieldValue.text = newValue
            holder.fieldValue.visibility = View.VISIBLE
            holder.fieldEdit.visibility = View.GONE
            //holder.spinner.visibility = if (field.fieldType == FieldType.DROPDOWN) View.VISIBLE else View.GONE
            holder.editButton.visibility = View.VISIBLE
            holder.confirmButton.visibility = View.GONE
            holder.cancelButton.visibility = View.GONE
            hideKeyboard(holder.fieldEdit)

            val isInvalidAfterEdit = when (field.key) {
                FieldKeys.TEMPO_IMPIEGATO, FieldKeys.NUMERO_ORDINI, FieldKeys.PAGA_BASE,
                FieldKeys.PAGA_TOTALE, FieldKeys.PAGA_ORARIA -> newValue == "0"
                FieldKeys.START_TIME, FieldKeys.END_TIME -> !newValue.matches(Regex("\\d{2}:\\d{2}"))
                FieldKeys.ORDER_STRATEGY -> newValue.toIntOrNull() !in 0..3
                else -> false
            } //|| field.isWarning
            val updatedTextColor = ContextCompat.getColor(
                context,
                if (isInvalidAfterEdit) android.R.color.holo_red_dark else R.color.text_color
            )
            holder.fieldName.setTextColor(updatedTextColor)
            holder.fieldValue.setTextColor(updatedTextColor)
            if (isInvalidAfterEdit) {
                holder.editButton.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark),
                    PorterDuff.Mode.SRC_IN
                )
            } else {
                holder.editButton.clearColorFilter()
            }
            updateSaveButtonState()
        }

        holder.cancelButton.setOnClickListener {
            holder.fieldValue.visibility = View.VISIBLE
            holder.fieldEdit.visibility = View.GONE
            //holder.spinner.visibility = if (field.fieldType == FieldType.DROPDOWN) View.VISIBLE else View.GONE
            holder.editButton.visibility = View.VISIBLE
            holder.confirmButton.visibility = View.GONE
            holder.cancelButton.visibility = View.GONE
            hideKeyboard(holder.fieldEdit)
            // Ripristina il valore originale per DROPDOWN
            if (field.fieldType == FieldType.DROPDOWN) {
                val originalValue = field.value.toIntOrNull() ?: 0
                holder.fieldValue.text = when (originalValue) {
                    OrderStrategyConstants.NORMAL -> field.dropdownOptions?.get(0) ?: "Normale"
                    OrderStrategyConstants.SERIAL -> field.dropdownOptions?.get(1) ?: "Seriale"
                    OrderStrategyConstants.PARALLEL -> field.dropdownOptions?.get(2) ?: "Parallelo"
                    OrderStrategyConstants.SERIAL_PARALLEL -> "${field.dropdownOptions?.get(1) ?: "Seriale"}, ${field.dropdownOptions?.get(2) ?: "Parallelo"}"
                    else -> field.value
                }
            }
        }
    }

    override fun getItemCount(): Int = fields.size

    private fun showKeyboard(editText: EditText) {
        editText.requestFocus()
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun showTimePickerDialog(context: Context, onTimeSelected: (String) -> Unit) {
        val timePicker = TimePickerDialog(
            context,
            { _, hour, minute ->
                val time = String.format("%02d:%02d", hour, minute)
                onTimeSelected(time)
            },
            0, 0, true
        )
        timePicker.show()
    }

    fun updateSaveButtonState() {
        if (fields.isEmpty()) {
            onValidationChanged(false) // Disabilita il pulsante di salvataggio se i campi non sono ancora popolati
            return
        }
        val isInvalid = fields.any { field ->
            when (field.key) {
                FieldKeys.TEMPO_IMPIEGATO, FieldKeys.NUMERO_ORDINI, FieldKeys.PAGA_BASE,
                FieldKeys.PAGA_TOTALE, FieldKeys.PAGA_ORARIA -> field.value == "0"
                FieldKeys.DATA -> field.value.isBlank()
                FieldKeys.START_TIME, FieldKeys.END_TIME -> !field.value.matches(Regex("\\d{2}:\\d{2}"))
                FieldKeys.ORDER_STRATEGY -> field.value.toIntOrNull() !in 0..3
                else -> false
            }
        }
        onValidationChanged(!isInvalid)
    }
}