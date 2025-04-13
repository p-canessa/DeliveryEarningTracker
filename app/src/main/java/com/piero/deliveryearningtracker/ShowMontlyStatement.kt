package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.Locale

class ShowMontlyStatement : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private var currentSummaryId: Int = 0
    private var adView: AdView? = null

    override fun onDestroy() {
        AdManager.destroyBannerAd(adView)
        adView = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AdManager.resumeBannerAd(adView)
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)
    }

    override fun onPause() {
        AdManager.pauseBannerAd(adView)
        super.onPause()
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.visualizza_statino)

        MobileAds.initialize(this) {}


        dbHelper = DatabaseHelper(this) // Inizializziamo dbHelper qui per usarlo subito

        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView, dbHelper)

        val toolbar: Toolbar = findViewById(R.id.vs_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        currentSummaryId = intent.getIntExtra("summary_id", 0)
        loadMontlyStatement(currentSummaryId)

        findViewById<Button>(R.id.btn_chiudi).text = getString(R.string.close)
        findViewById<Button>(R.id.btn_chiudi).setOnClickListener {
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

    private fun loadMontlyStatement(summaryId: Int) {
        if (summaryId != 0) {
            val summary = dbHelper.getMonthlySummary(summaryId)
            val orders = dbHelper.getDailyOrders(summaryId)

            val monthName = java.text.DateFormatSymbols(Locale.getDefault()).months[summary.monthNumber - 1]
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val formattedMonth = "$monthName ${summary.year}"
            findViewById<TextView>(R.id.tv_mese).text = formattedMonth
            val recyclerView = findViewById<RecyclerView>(R.id.rv_ordini_giornalieri)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = DailyOrdersAdapter(orders)

            val tableLayout = findViewById<TableLayout>(R.id.riepilogo_table)
            populateRiepilogoTable(tableLayout, summary)
            setupNavigationButtons()
        }
    }

    private fun setupNavigationButtons() {
        findViewById<ImageButton>(R.id.btn_precedente).setOnClickListener {
            val previousSummaryId = dbHelper.getPreviousSummaryId(currentSummaryId)
            if (previousSummaryId != -1) {
                currentSummaryId = previousSummaryId
                loadMontlyStatement(currentSummaryId)
            }
        }

        findViewById<ImageButton>(R.id.btn_successivo).setOnClickListener {
            val nextSummaryId = dbHelper.getNextSummaryId(currentSummaryId)
            if (nextSummaryId != -1) {
                currentSummaryId = nextSummaryId
                loadMontlyStatement(currentSummaryId)
            }
        }
    }

    private fun populateRiepilogoTable(tableLayout: TableLayout, summary: RiepilogoData) {
        tableLayout.removeAllViews()

        when (summary.taxRegime) {
            "Ritenuta d'acconto" -> {
                val colTitles = listOf("", getString(R.string.statement_gross), getString(R.string.statement_withholding_tax), getString(R.string.statement_total))

                val titleRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                for (title in colTitles) {
                    val titleTextView = TextView(this).apply {
                        text = title
                        setPadding(8, 8, 8, 8)
                        gravity = android.view.Gravity.CENTER
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    titleRow.addView(titleTextView)
                }
                tableLayout.addView(titleRow)

                val ordiniRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                ordiniRow.addView(createTextView(getString(R.string.statement_orders), true))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniLordo)))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniImportoRitenuta ?: 0.0)))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniTotale)))
                tableLayout.addView(ordiniRow)

                val integrazioniRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                integrazioniRow.addView(createTextView(getString(R.string.statement_integrations), true))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniLordo)))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniImportoRitenuta ?: 0.0)))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniTotale)))
                tableLayout.addView(integrazioniRow)

                val manceRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                manceRow.addView(createTextView(getString(R.string.statement_tips), true))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceLordo)))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceImportoRitenuta ?: 0.0)))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceTotale)))
                tableLayout.addView(manceRow)

                val totaleRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                totaleRow.addView(createTextView(getString(R.string.statement_total), true))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleLordo)))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleImportoRitenuta ?: 0.0)))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleTotale)))
                tableLayout.addView(totaleRow)
            }
            "Regime Forfettario" -> {
                val rowTitles = listOf(getString(R.string.statement_orders), getString(R.string.statement_integrations), getString(R.string.statement_tips), getString(R.string.statement_total))
                val rowValues = listOf(summary.ordiniTotale, summary.integrazioniTotale, summary.manceTotale, summary.totaleTotale)

                val titleRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                for (title in rowTitles) {
                    val titleTextView = TextView(this).apply {
                        text = title
                        setPadding(8, 8, 8, 8)
                        gravity = android.view.Gravity.CENTER
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    titleRow.addView(titleTextView)
                }
                tableLayout.addView(titleRow)

                val valueRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                for (value in rowValues) {
                    val valueTextView = TextView(this).apply {
                        text = CurrencyFormatter.format(value)
                        setPadding(8, 8, 8, 8)
                        gravity = android.view.Gravity.END
                    }
                    valueRow.addView(valueTextView)
                }
                tableLayout.addView(valueRow)
            }
            "Regime Ordinario" -> {
                val colTitles = listOf("", getString(R.string.statement_gross), getString(R.string.statement_vat), getString(R.string.statement_total))

                val titleRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                for (title in colTitles) {
                    val titleTextView = TextView(this).apply {
                        text = title
                        setPadding(8, 8, 8, 8)
                        gravity = android.view.Gravity.CENTER
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    titleRow.addView(titleTextView)
                }
                tableLayout.addView(titleRow)

                val ordiniRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                ordiniRow.addView(createTextView(getString(R.string.statement_orders), true))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniLordo)))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniImportoIva ?: 0.0)))
                ordiniRow.addView(createTextView(CurrencyFormatter.format(summary.ordiniTotale)))
                tableLayout.addView(ordiniRow)

                val integrazioniRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                integrazioniRow.addView(createTextView(getString(R.string.statement_integrations), true))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniLordo)))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniImportoIva ?: 0.0)))
                integrazioniRow.addView(createTextView(CurrencyFormatter.format(summary.integrazioniTotale)))
                tableLayout.addView(integrazioniRow)

                val manceRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                manceRow.addView(createTextView(getString(R.string.statement_tips), true))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceLordo)))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceImportoIva ?: 0.0)))
                manceRow.addView(createTextView(CurrencyFormatter.format(summary.manceTotale)))
                tableLayout.addView(manceRow)

                val totaleRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
                totaleRow.addView(createTextView(getString(R.string.statement_total), true))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleLordo)))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleImportoIva ?: 0.0)))
                totaleRow.addView(createTextView(CurrencyFormatter.format(summary.totaleTotale)))
                tableLayout.addView(totaleRow)
            }
        }

        val riscossiRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
        val riscossiTitle = TextView(this).apply {
            text = getString(R.string.statement_cash_collected)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
            gravity = android.view.Gravity.START
        }
        val riscossiValue = TextView(this).apply {
            text = CurrencyFormatter.format(summary.pagamentiContanti ?: 0.0)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.END
        }
        riscossiRow.addView(riscossiTitle)
        riscossiRow.addView(riscossiValue)
        tableLayout.addView(riscossiRow)

        val lineRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
        for (i in 0..2) {
            val emptyCell = TextView(this).apply { layoutParams = TableRow.LayoutParams(0, 0, 1f) }
            lineRow.addView(emptyCell)
        }
        val lineView = View(this).apply {
            layoutParams = TableRow.LayoutParams(0, 2, 1f)
            setBackgroundColor(getColor(R.color.text_color))
        }
        lineRow.addView(lineView)
        tableLayout.addView(lineRow)

        val totalDueRow = TableRow(this).apply { layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT) }
        val totalDueTitle = TextView(this).apply {
            text = getString(R.string.statement_total_due)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
            gravity = android.view.Gravity.START
        }
        val totalDueValue = TextView(this).apply {
            text = CurrencyFormatter.format(summary.totaleDovuto ?: 0.0)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.END
        }
        totalDueRow.addView(totalDueTitle)
        totalDueRow.addView(totalDueValue)
        tableLayout.addView(totalDueRow)
    }

    private fun createTextView(text: String, isHeader: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(8, 8, 8, 8)
        if (isHeader) {
            tv.gravity = android.view.Gravity.START
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            tv.gravity = android.view.Gravity.END
        }
        return tv
    }
}