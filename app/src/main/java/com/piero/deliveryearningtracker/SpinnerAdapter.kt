package com.piero.deliveryearningtracker

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class SpinnerAdapter(
    context: Context,
    resource: Int,
    objects: List<String>
) : ArrayAdapter<String>(context, resource, objects) {

    // Verifica se l'app è in modalità scura
    private val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    // Imposta il colore del testo: bianco in modalità scura, nero in modalità chiara
    private val textColor = if (isDarkMode) Color.WHITE else Color.BLACK

    // Configura l'elemento selezionato nello spinner
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.spinner_text)
        textView.setTextColor(textColor)
        return view
    }

    // Configura gli elementi nella tendina dello spinner
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.spinner_text)
        textView.setTextColor(textColor)
        return view
    }
}