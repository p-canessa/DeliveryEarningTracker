package com.piero.deliveryearningtracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.graphics.Typeface

object MultiAppUtils {
    fun showMultiAppDialog(
        context: Context,
        order: OrderData,
        candidates: List<CandidateOrder>,
        dbHelper: DatabaseHelper,
        onConfirm: (Int, Map<Long, Int>) -> Unit
    ) {
        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle(context.getString(R.string.multiapp_dialog_title))

        // Crea un layout per la dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_multiapp, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.strategy_container)

        // Mappa per memorizzare le strategie selezionate per ogni ordine candidato
        val selectedStrategies = mutableMapOf<Long, Int>()
        val currentStart = LocalTime.parse(order.startTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))
        val currentEnd = LocalTime.parse(order.endTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))

        // Crea dinamicamente un CardView per ogni ordine candidato
        candidates.forEachIndexed { index, candidate ->
            val candidateStart = LocalTime.parse(candidate.StartTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))
            val candidateEnd = LocalTime.parse(candidate.EndTime.toString().substring(0, 5), DateTimeFormatter.ofPattern("HH:mm"))
            val isFullyContained = !candidateStart.isBefore(currentStart) && !candidateEnd.isAfter(currentEnd)

            // Imposta la strategia di default
            selectedStrategies[candidate.ID] = if (isFullyContained) OrderStrategyConstants.PARALLEL else OrderStrategyConstants.SERIAL

            // Crea un CardView per l'ordine candidato
            val cardView = CardView(context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                cardElevation = 4f
                radius = 8f
                setCardBackgroundColor(ContextCompat.getColorStateList(context, android.R.color.background_light))
            }

            // Contenitore interno del CardView
            val cardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
            }

            val title = TextView(context).apply {
                text = context.getString(
                    R.string.multiapp_card_title,
                    candidate.ProviderName,
                    candidate.Ristorante,
                    candidate.StartTime.toString().substring(0, 5),
                    candidate.EndTime.toString().substring(0, 5)
                )
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColorStateList(context, android.R.color.primary_text_light))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }

            val radioContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val radioGroup = RadioGroup(context).apply {
                id = View.generateViewId()
                orientation = RadioGroup.HORIZONTAL
            }

            val radioParallel = RadioButton(context).apply {
                id = View.generateViewId()
                text = context.getString(R.string.multiapp_parallel)
                isChecked = isFullyContained // Default se solo parallelo
                setTextColor(ContextCompat.getColorStateList(context, android.R.color.primary_text_light))
            }

            if (!isFullyContained) {
                val radioSerial = RadioButton(context).apply {
                    id = View.generateViewId()
                    text = context.getString(R.string.multiapp_serial)
                    isChecked = true // Default se possibile
                    setTextColor(ContextCompat.getColorStateList(context, android.R.color.primary_text_light))
                }
                radioGroup.addView(radioSerial)
            }
            radioGroup.addView(radioParallel)

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                selectedStrategies[candidate.ID] = when (checkedId) {
                    radioParallel.id -> OrderStrategyConstants.PARALLEL
                    else -> if (isFullyContained) OrderStrategyConstants.PARALLEL else OrderStrategyConstants.SERIAL
                }
            }

            radioContainer.addView(radioGroup)
            cardContainer.addView(title)
            cardContainer.addView(radioContainer)
            cardView.addView(cardContainer)
            container.addView(cardView)
        }

        dialogBuilder.setView(dialogView)

        // Crea e mostra la dialog
        val dialog = dialogBuilder.create()
        dialog.setCancelable(false) // Impedisce la chiusura con il tasto indietro
        dialog.show()

        // Collega i pulsanti personalizzati
        dialogView.findViewById<Button>(R.id.button_confirm).setOnClickListener {
            val finalStrategy = selectedStrategies.values.reduceOrNull { acc, strategy -> acc or strategy }
                ?: OrderStrategyConstants.PARALLEL
            onConfirm(finalStrategy, selectedStrategies)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.button_cancel).setOnClickListener {
            onConfirm(order.orderStrategy, selectedStrategies)
            dialog.dismiss()
        }
    }
}