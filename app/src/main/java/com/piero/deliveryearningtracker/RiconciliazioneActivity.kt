package com.piero.deliveryearningtracker

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

class RiconciliazioneActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private var currentMonth: Int = 1
    private var currentYear: Int = 2025

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riconciliazione)
        dbHelper = DatabaseHelper(this)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.btn_prev_month).setOnClickListener { changeMonth(-1) }
        findViewById<ImageButton>(R.id.btn_next_month).setOnClickListener { changeMonth(1) }

        updateUI()
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

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun changeMonth(delta: Int) {
        currentMonth += delta
        if (currentMonth > 12) {
            currentMonth = 1
            currentYear++
        } else if (currentMonth < 1) {
            currentMonth = 12
            currentYear--
        }
        updateUI()
    }

    private fun updateUI() {
        val monthStr = String.format(Locale.getDefault(), "%02d", currentMonth)
        findViewById<TextView>(R.id.tv_current_month).text = String.format("%s/%s", monthStr, currentYear)
        val textColor = ContextCompat.getColor(this, R.color.text_color)

        val dailyData = dbHelper.getDailyReconciliationData(monthStr, currentYear)
        val monthlyData = dbHelper.getMonthlyReconciliationData(monthStr, currentYear)

        val table = findViewById<TableLayout>(R.id.table_reconciliation)
        table.removeAllViews()

        addTableRow(
            table,
            texts = listOf(
                getString(R.string.reconciliation_date),
                getString(R.string.reconciliation_daily_compensation),
                getString(R.string.reconciliation_orders_compensation),
                getString(R.string.reconciliation_daily_orders),
                getString(R.string.reconciliation_orders_count)
            ),
            isHeader = true
        )

        for (day in dailyData) {
            val compensoColor = if (roundToTwoDecimals(day.dailyTotalGross) == roundToTwoDecimals(day.ordersTotalGross)) Color.GREEN else Color.RED
            val ordiniColor = if (day.dailyNumberOfOrders == day.ordersCount) Color.GREEN else Color.RED
            val hasError = compensoColor == Color.RED || ordiniColor == Color.RED

            addTableRow(
                table,
                texts = listOf(
                    day.date,
                    CurrencyFormatter.format(day.dailyTotalGross),
                    CurrencyFormatter.format(day.ordersTotalGross),
                    day.dailyNumberOfOrders.toString(),
                    day.ordersCount.toString()
                ),
                colors = listOf(textColor, compensoColor, compensoColor, ordiniColor, ordiniColor),
                onClick = if (hasError) {
                    {
                        val intent = Intent(this, ErrorOrdersActivity::class.java).apply {
                            putExtra("date", day.date)
                            putExtra("error_type", if (compensoColor == Color.RED && ordiniColor == Color.GREEN) "compenso" else "entrambi")
                        }
                        startActivity(intent)
                    }
                } else null
            )
        }

        addTableRow(
            table,
            texts = listOf(
                getString(R.string.reconciliation_monthly_totals),
                getString(R.string.reconciliation_statement),
                getString(R.string.reconciliation_orders),
                getString(R.string.reconciliation_difference),
                ""
            ),
            isHeader = true
        )

        val manceColor = if (roundToTwoDecimals(monthlyData.manceLordo) == roundToTwoDecimals(monthlyData.ordersMancia)) Color.GREEN else Color.RED
        val contantiColor = if (roundToTwoDecimals(monthlyData.pagamentiContanti) == roundToTwoDecimals(monthlyData.ordersRiscossiContanti)) Color.GREEN else Color.RED

        addTableRow(
            table,
            texts = listOf(
                getString(R.string.reconciliation_tips),
                CurrencyFormatter.format(monthlyData.manceLordo),
                CurrencyFormatter.format(monthlyData.ordersMancia),
                CurrencyFormatter.format(monthlyData.manceLordo - monthlyData.ordersMancia),
                ""
            ),
            colors = listOf(
                textColor,
                manceColor,
                manceColor,
                if (roundToTwoDecimals(monthlyData.manceLordo) >= roundToTwoDecimals(monthlyData.ordersMancia)) Color.GREEN else Color.RED,
                textColor
            ),
            onClick = if (manceColor == Color.RED) {
                {
                    val intent = Intent(this, ErrorOrdersActivity::class.java).apply {
                        putExtra("month", currentMonth)
                        putExtra("year", currentYear)
                        putExtra("error_type", if (monthlyData.ordersMancia < monthlyData.manceLordo) "mancia_minore" else "mancia_maggiore")
                    }
                    startActivity(intent)
                }
            } else null
        )

        addTableRow(
            table,
            texts = listOf(
                getString(R.string.reconciliation_cash),
                CurrencyFormatter.format(monthlyData.pagamentiContanti),
                CurrencyFormatter.format(monthlyData.ordersRiscossiContanti),
                CurrencyFormatter.format(monthlyData.pagamentiContanti - monthlyData.ordersRiscossiContanti),
                ""
            ),
            colors = listOf(
                textColor,
                contantiColor,
                contantiColor,
                if (roundToTwoDecimals(monthlyData.pagamentiContanti) >= roundToTwoDecimals(monthlyData.ordersRiscossiContanti)) Color.GREEN else Color.RED,
                textColor
            ),
            onClick = if (contantiColor == Color.RED) {
                {
                    val intent = Intent(this, ErrorOrdersActivity::class.java).apply {
                        putExtra("month", currentMonth)
                        putExtra("year", currentYear)
                        putExtra("error_type", if (monthlyData.ordersRiscossiContanti > monthlyData.pagamentiContanti) "contanti_maggiore" else "contanti_minore")
                    }
                    startActivity(intent)
                }
            } else null
        )
    }

    private fun addTableRow(table: TableLayout, texts: List<String>, isHeader: Boolean = false, colors: List<Int> = emptyList(), onClick: (() -> Unit)? = null) {
        val row = TableRow(this)
        for (i in texts.indices) {
            val tv = TextView(this).apply {
                text = texts[i]
                setPadding(8, 8, 8, 8)
                gravity = Gravity.CENTER
                if (isHeader) setTypeface(null, Typeface.BOLD)
                if (i < colors.size && !isHeader) setTextColor(colors[i])
            }
            row.addView(tv)
        }
        if (!isHeader && onClick != null) {
            row.setOnClickListener { onClick() }
        }
        table.addView(row)
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}