package com.piero.deliveryearningtracker

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class FieldItem(val name: String, var value: String, val isDate: Boolean = false)

class DataAdapter(
    private val fields: MutableList<FieldItem>,
    private val onValueChanged: (Int, String) -> Unit
) : RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fieldName: TextView = itemView.findViewById(R.id.field_name)
        val fieldValue: TextView = itemView.findViewById(R.id.field_value)
        val fieldEdit: EditText = itemView.findViewById(R.id.field_edit)
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
        holder.fieldName.text = field.name
        holder.fieldValue.text = field.value
        // Controlla la posizione e gestisci la visibilità del bottone "edit"
        if (position == 8 || position == 9) {
            holder.editButton.visibility = View.GONE
        } else {
            holder.editButton.visibility = View.VISIBLE
        }

        holder.editButton.setOnClickListener {
            holder.fieldValue.visibility = View.GONE
            holder.fieldEdit.setText(field.value)
            holder.fieldEdit.visibility = View.VISIBLE
            holder.fieldEdit.selectAll()  // Seleziona tutto il testo nella EditText
            holder.fieldEdit.requestFocus()  // Dà il focus alla EditText
            showKeyboard(holder.fieldEdit)
            holder.editButton.visibility = View.GONE
            holder.confirmButton.visibility = View.VISIBLE
            holder.cancelButton.visibility = View.VISIBLE
        }

        holder.confirmButton.setOnClickListener {
            val newValue = holder.fieldEdit.text.toString().replace(",",".")
            Log.d("DataAdapter", "${fields[position].name}.${fields[position].value} <- $newValue")
            fields[position].value = newValue
            onValueChanged(position, newValue)
            holder.fieldValue.text = newValue
            holder.fieldValue.visibility = View.VISIBLE
            holder.fieldEdit.visibility = View.GONE
            holder.editButton.visibility = View.VISIBLE
            holder.confirmButton.visibility = View.GONE
            holder.cancelButton.visibility = View.GONE
            hideKeyboard(holder.fieldEdit)
        }

        holder.cancelButton.setOnClickListener {
            holder.fieldValue.visibility = View.VISIBLE
            holder.fieldEdit.visibility = View.GONE
            holder.editButton.visibility = View.VISIBLE
            holder.confirmButton.visibility = View.GONE
            holder.cancelButton.visibility = View.GONE
            hideKeyboard(holder.fieldEdit)
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
}