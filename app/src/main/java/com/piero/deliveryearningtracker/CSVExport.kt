package com.piero.deliveryearningtracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStreamWriter
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import android.database.sqlite.SQLiteDatabase
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import java.text.DecimalFormat

class ExportActivity : AppCompatActivity() {

    private lateinit var editTextStartDate: EditText
    private lateinit var editTextEndDate: EditText
    private lateinit var buttonExport: Button
    private lateinit var dbHelper: DatabaseHelper

    // Launcher per il SAF
    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            exportDataToCSV(uri)
        } else {
            Toast.makeText(this, getString(R.string.csv_error_abort), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csvexport)

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

        editTextStartDate = findViewById(R.id.editTextStartDate)
        editTextEndDate = findViewById(R.id.editTextEndDate)
        buttonExport = findViewById(R.id.buttonExport)
        dbHelper = DatabaseHelper(this)

        // Listener per aprire il DatePickerDialog
        editTextStartDate.setOnClickListener { showDatePickerDialog(editTextStartDate) }
        editTextEndDate.setOnClickListener { showDatePickerDialog(editTextEndDate) }

        // Listener per il pulsante di esportazione
        buttonExport.setOnClickListener {
            val startDate = editTextStartDate.text.toString()
            val endDate = editTextEndDate.text.toString()
            val fileName = "ordini_${startDate}_to_${endDate}.csv".takeIf { startDate.isNotEmpty() && endDate.isNotEmpty() }
                ?: "ordini.csv"
            createDocumentLauncher.launch(fileName)
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

    // Funzione per mostrare il DatePickerDialog
    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                editText.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    // Funzione per recuperare i dati dal database
    private fun getDataFromDatabase(startDate: String?, endDate: String?): android.database.Cursor {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val query = when {
            startDate != null && endDate != null -> "SELECT * FROM ordini WHERE Data BETWEEN ? AND ?"
            startDate != null -> "SELECT * FROM ordini WHERE Data >= ?"
            endDate != null -> "SELECT * FROM ordini WHERE Data <= ?"
            else -> "SELECT * FROM ordini"
        }
        val params = when {
            startDate != null && endDate != null -> arrayOf(startDate, endDate)
            startDate != null -> arrayOf(startDate)
            endDate != null -> arrayOf(endDate)
            else -> emptyArray()
        }
        return db.rawQuery(query, params)
    }

    // Funzione per esportare i dati in CSV
    private fun exportDataToCSV(uri: android.net.Uri) {
        val startDate = editTextStartDate.text.toString().takeIf { it.isNotEmpty() }
        val endDate = editTextEndDate.text.toString().takeIf { it.isNotEmpty() }
        val cursor = getDataFromDatabase(startDate, endDate)
        val decimalFormat =DecimalFormat.getInstance(Locale.getDefault()) as DecimalFormat

        // Determina il delimitatore in base al locale
        val decimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator
        val delimiter = if (decimalSeparator == ',') ';' else ','

        decimalFormat.applyPattern("0.00")
        try {
            val outputStream = contentResolver.openOutputStream(uri)
            val writer = OutputStreamWriter(outputStream)
            // Intestazione del CSV
            writer.append(String.format(getString(R.string.csv_header),delimiter))

            // Scrittura dei dati
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"))
                val data = cursor.getString(cursor.getColumnIndexOrThrow("Data"))
                val pagaBase = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaBase"))
                val pagaExtra = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaExtra"))
                val mancia = cursor.getDouble(cursor.getColumnIndexOrThrow("Mancia"))
                val numeroordini = cursor.getInt(cursor.getColumnIndexOrThrow("NumeroOrdini"))
                val tempoimpiegato = cursor.getInt(cursor.getColumnIndexOrThrow("TempoImpiegato"))
                val pagatotale = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaTotale"))
                val pagaoraria = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaOraria"))

                writer.append("$id$delimiter$data$delimiter${decimalFormat.format(pagaBase)}$delimiter${decimalFormat.format(pagaExtra)}$delimiter${decimalFormat.format(mancia)}${delimiter}$numeroordini${delimiter}$tempoimpiegato${delimiter}${decimalFormat.format(pagatotale)}${delimiter}${decimalFormat.format(pagaoraria)}\n")
            }

            writer.flush()
            writer.close()
            cursor.close()

            Toast.makeText(this, getString(R.string.csv_export_success), Toast.LENGTH_LONG).show()
            finish() // Chiude l'attività
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.csv_error_failed), e.message), Toast.LENGTH_LONG).show()
        }
    }
}