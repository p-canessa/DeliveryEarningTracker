package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.piero.deliveryearningtracker.utils.sendAnonymousStats
import com.piero.deliveryearningtracker.utils.scheduleStatsUpload
import java.util.UUID
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * MainActivity is the primary entry point of the delivery earnings tracking app. It serves as the main
 * dashboard for riders to view and manage their delivery orders and earnings from multiple providers
 * (Deliveroo and Glovo). Key features include:
 * - Displaying a list of orders with provider-specific headers using a RecyclerView.
 * - Showing aggregated totals (base pay, extra pay, tips, etc.) for a selected date range.
 * - Providing navigation to export data, import PDFs, view monthly statements, and access settings.
 * - Managing ads and subscriptions via AdManager and BillingManager.
 * - Handling anonymous statistics upload if enabled.
 * - Supporting referral tracking via InstallReferrerClient.
 *
 * External Dependencies:
 * - Layout: res/layout/activity_main.xml (UI components like RecyclerView, buttons, and drawer).
 * - Menu: res/menu/menu_main.xml (options menu for help action).
 * - Classes: DatabaseHelper, OrderAdapter, DateRangeSelector, OrderItemView, AdManager,
 *   BillingManager, CurrencyFormatter, MyApplication.
 * - Utilities: utils.kt (sendAnonymousStats, scheduleStatsUpload).
 * - Libraries: Firebase Firestore, Google Mobile Ads, InstallReferrerClient.
 * - SharedPreferences: Default preferences for settings (night_mode, currency_symbol, share_anonymous_stats).
 *
 * Design Choices:
 * - Uses a DrawerLayout for navigation to keep the UI clean and accessible.
 * - Integrates multiple providers (Deliveroo, Glovo) by leveraging DatabaseHelper to store
 *   provider-specific data and OrderAdapter to display provider-based order grouping.
 * - Implements a subscription-based ad removal system to enhance user experience.
 * - Ensures robust error handling for database and ad operations to prevent crashes.
 */
class MainActivity : AppCompatActivity() {
    // Database helper for accessing order and provider data
    private lateinit var dbHelper: DatabaseHelper
    // Custom view for selecting date ranges (day, week, month)
    private lateinit var dateRangeSelector: DateRangeSelector
    // TextViews for displaying aggregated totals
    private lateinit var totalPagaBase: TextView
    private lateinit var totalPagaExtra: TextView
    private lateinit var totalMancia: TextView
    private lateinit var totalPagaTotale: TextView
    private lateinit var totalNumeroOrdini: TextView
    private lateinit var totalTempoImpiegato: TextView
    private lateinit var totalPagaOraria: TextView
    // RecyclerView for displaying orders grouped by provider
    private lateinit var recyclerView: RecyclerView
    // Adapter for managing order list with provider headers
    private lateinit var orderAdapter: OrderAdapter
    private val visibilityMap = mutableMapOf<Int, Boolean>() // Mappa di visibilità
    private var currentOrders: List<Order> = emptyList() // Cache ordini
    private var currentSqlClause: String = "" // Cache SQL clause
    // Buttons for adding orders manually or via OCR
    private lateinit var addOrderButton: Button
    private lateinit var addOrderButtonOCR: Button
    // Drawer layout for navigation menu
    private lateinit var drawerLayout: DrawerLayout
    // SharedPreferences for storing user settings
    private lateinit var sharedPref: SharedPreferences
    // Listener for preference changes (e.g., currency symbol)
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    // AdView for displaying banner ads
    private var adView: AdView? = null
    // InstallReferrerClient for tracking referral codes
    private lateinit var referrerClient: InstallReferrerClient

    /**
     * Listener for subscription updates from BillingManager.
     * Updates ad visibility based on subscription status.
     * Dependencies: AdManager, BillingManager.
     */
    private val subscriptionListener: (Boolean, String?) -> Unit = { isSubscribed, message ->
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView)
        Log.d("MainActivity", "Subscription updated: isSubscribed=$isSubscribed, message=$message")
        if (isSubscribed) {
            Log.d("MainActivity", "Annunci disattivati")
        } else {
            Log.d("MainActivity", "Annunci abilitati")
        }
    }

    /**
     * Initializes the activity, setting up the UI, database, ads, and navigation.
     * Applies night mode based on user preference and initializes key components.
     * Dependencies: MyApplication, DatabaseHelper, CurrencyFormatter, AdManager, BillingManager,
     * utils.kt (sendAnonymousStats, scheduleStatsUpload), activity_main.xml.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Load user preferences for night mode
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val nightModeEnabled = sharedPref.getBoolean("night_mode", false)

        // Apply night mode before loading the layout
        AppCompatDelegate.setDefaultNightMode(
            if (nightModeEnabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // Initialize DatabaseHelper from MyApplication
        dbHelper = (application as MyApplication).dbHelper

        // Register listener for currency symbol changes
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "currency_symbol") {
                CurrencyFormatter.initialize(this)
                updateTotals()
                updateOrderList()
            }
        }
        sharedPref.registerOnSharedPreferenceChangeListener(preferenceListener)

        // Set the main layout
        setContentView(R.layout.activity_main)
        // Check for referral codes
        checkInstallReferrer()

        // Initialize Google Mobile Ads
        MobileAds.initialize(this) {
            Log.d("AdMob", "Inizializzazione completata")
        }

        // Register subscription listener
        BillingManager.getInstance(this).addSubscriptionListener(subscriptionListener)

        // Set up banner ads
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView)

        // Schedule anonymous stats upload if enabled
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isStatsEnabled = sharedPrefs.getBoolean("share_anonymous_stats", false)
        if (isStatsEnabled) {
            sendAnonymousStats(this)
            scheduleStatsUpload(this)
        }

        // Initialize add order buttons and ensure consistent height
        addOrderButton = findViewById(R.id.add_order_button)
        addOrderButtonOCR = findViewById(R.id.add_order_button_OCR)
        addOrderButton.post {
            addOrderButton.measure(
                View.MeasureSpec.makeMeasureSpec(addOrderButton.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            addOrderButtonOCR.measure(
                View.MeasureSpec.makeMeasureSpec(addOrderButtonOCR.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val height1 = addOrderButton.measuredHeight
            val height2 = addOrderButtonOCR.measuredHeight
            val maxHeight = maxOf(height1, height2)

            val params1 = addOrderButton.layoutParams
            params1.height = maxHeight
            addOrderButton.layoutParams = params1

            val params2 = addOrderButtonOCR.layoutParams
            params2.height = maxHeight
            addOrderButtonOCR.layoutParams = params2

            addOrderButton.requestLayout()
            addOrderButtonOCR.requestLayout()
        }

        // Set up the toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize the navigation drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Handle navigation menu item clicks
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_csv_export -> {
                    startActivity(Intent(this, ExportActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_pdf_import -> {
                    startActivity(Intent(this, PDFImport::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_show_statement -> {
                    val intent = Intent(this, ShowMontlyStatement::class.java)
                    intent.putExtra("summary_id", getDefaultSummaryId())
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_trimestre -> {
                    startActivity(Intent(this, TrimestreActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_anno -> {
                    startActivity(Intent(this, AnnoActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_riconciliazione -> {
                    startActivity(Intent(this, RiconciliazioneActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        // Initialize UI components
        dateRangeSelector = findViewById(R.id.date_range_selector)
        totalPagaBase = findViewById(R.id.total_paga_base)
        totalPagaBase.text = getString(R.string.total_paga_base)
        totalPagaExtra = findViewById(R.id.total_paga_extra)
        totalPagaExtra.text = getString(R.string.total_paga_extra)
        totalMancia = findViewById(R.id.total_mancia)
        totalMancia.text = getString(R.string.total_mancia)
        totalPagaTotale = findViewById(R.id.total_paga_totale)
        totalPagaTotale.text = getString(R.string.total_paga_totale)
        totalNumeroOrdini = findViewById(R.id.total_numero_ordini)
        totalNumeroOrdini.text = getString(R.string.total_numero_ordini)
        totalTempoImpiegato = findViewById(R.id.total_tempo_impiegato)
        totalTempoImpiegato.text = getString(R.string.total_tempo_impiegato)
        totalPagaOraria = findViewById(R.id.total_paga_oraria)
        totalPagaOraria.text = getString(R.string.total_paga_oraria)
        // Initialize RecyclerView and adapter
        recyclerView = findViewById(R.id.orders_recycler_view)
        orderAdapter = OrderAdapter(dbHelper)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter
        // Imposta il listener per il click sull'header
        orderAdapter.setOnHeaderClickListener { providerId ->
            // Toggle visibilità
            visibilityMap[providerId] = !(visibilityMap[providerId] ?: true)
            // Aggiorna la lista con la nuova mappa di visibilità
            orderAdapter.updateOrders(currentOrders, currentSqlClause, visibilityMap)
            Log.d("MainActivity", "Toggled provider $providerId to ${visibilityMap[providerId]}")
        }

        // Ripristina lo stato della visibilità (se presente)
        savedInstanceState?.let { bundle ->
            @Suppress("DEPRECATION") // Necessario per compatibilità con minSdk 29
            val savedVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable("visibilityMap", HashMap::class.java)
            } else {
                bundle.getSerializable("visibilityMap")
            }
            if (savedVisibility is HashMap<*, *> && savedVisibility.all { it.key is Int && it.value is Boolean }) {
                @Suppress("UNCHECKED_CAST")
                visibilityMap.putAll(savedVisibility as HashMap<Int, Boolean>)
                Log.d("MainActivity", "Restored visibilityMap: $visibilityMap")
            } else {
                Log.w("MainActivity", "Invalid visibilityMap in savedInstanceState")
            }
        }

        recyclerView.isNestedScrollingEnabled = true
        Log.d("RecyclerDebug", "RecyclerView configurato")

        // Log RecyclerView height
        recyclerView.post {
            Log.d("RecyclerDebug", "Height: ${recyclerView.height}")
        }

        // Set date range change listener to refresh data
        dateRangeSelector.setOnChangeListener(object : DateRangeSelector.OnChangeListener {
            override fun onChange() {
                visibilityMap.clear()
                updateTotals()
                updateOrderList()
            }
        })

        // Set up manual order addition
        addOrderButton.setOnClickListener {
            val orderItemView = OrderItemView(this)
            orderItemView.setOnOrderSavedListener {
                updateTotals()
                updateOrderList()
            }
            orderItemView.showEditDialog()
        }

        // Set up OCR-based order addition
        addOrderButtonOCR.setOnClickListener {
            startActivity(Intent(this, ImageRecognitionActivity::class.java))
        }

        // Set order deletion listener
        orderAdapter.setOnOrderDeletedListener {
            updateTotals()
            updateOrderList()
        }

        // Set order saved listener
        orderAdapter.setOnOrderSavedListener {
            updateTotals()
            updateOrderList()
        }

        // Initial data load
        updateTotals()
        updateOrderList()
    }

    /**
     * Resumes the activity, refreshing ads and data.
     * Dependencies: AdManager, BillingManager.
     */
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume chiamato")
        AdManager.resumeBannerAd(adView)
        BillingManager.getInstance(this).checkSubscription()
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.updateAds(this, adContainer, adView)
        updateTotals()
        updateOrderList()
    }

    /**
     * Cleans up resources when the activity is destroyed.
     * Dependencies: AdManager, BillingManager.
     */
    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy chiamato")
        BillingManager.getInstance(this).removeSubscriptionListener(subscriptionListener)
        AdManager.destroyBannerAd(adView)
        adView = null
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        referrerClient.endConnection()
        super.onDestroy()
    }

    /**
     * Pauses banner ads when the activity is paused.
     * Dependencies: AdManager.
     */
    override fun onPause() {
        AdManager.pauseBannerAd(adView)
        super.onPause()
    }

    /**
     * Inflates the options menu.
     * Dependencies: menu_main.xml.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * Handles options menu item selections.
     * Launches HelpActivity for the help action.
     */
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("visibilityMap", HashMap(visibilityMap))
    }

    /**
     * Updates the order list in the RecyclerView based on the selected date range.
     * Queries DatabaseHelper for orders and updates OrderAdapter.
     * Dependencies: DatabaseHelper, OrderAdapter, DateRangeSelector.
     * Design Choice: Groups orders by provider (Deliveroo, Glovo) for clarity.
     */
    private fun updateOrderList() {
        try {
            val sqlClause = dateRangeSelector.getSqlClause("o.Data")
            Log.d("RecyclerDebug", "SQL Clause: $sqlClause}")
            val orders = dbHelper.getOrders(sqlClause)
            Log.d("RecyclerDebug", "Orders Found: $orders")
            currentOrders = orders // Cache ordini
            currentSqlClause = sqlClause // Cache SQL clause
            orderAdapter.updateOrders(orders, sqlClause, visibilityMap)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating order list: ${e.message}", e)
            orderAdapter.updateOrders(emptyList(), "", visibilityMap)
        }
    }

    /**
     * Converts minutes to a formatted string (e.g., "2h 30m").
     * @param minutes Total minutes to convert.
     * @return Formatted time string.
     */
    private fun convertMinutesToHoursMinutes(minutes: Int): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (minutes > 60) {
            getString(R.string.time_format_hours_minutes, hours, remainingMinutes)
        } else {
            getString(R.string.time_format_minutes, minutes)
        }
    }
    /**
     * Updates the totals displayed in TextViews based on the selected date range.
     * Dependencies: DatabaseHelper, CurrencyFormatter, DateRangeSelector.
     * Design Choice: Shows both total and cash amounts for transparency.
     */
    @SuppressLint("SetTextI18n")
    private fun updateTotals() {
        try {
            val sqlClause = dateRangeSelector.getSqlClause("Data")
            val totali = dbHelper.getTotali(sqlClause)
            if (totali != null) {
                val strTempo = convertMinutesToHoursMinutes(totali.totalTempoImpiegato)
                val formattedPagaBase = "${CurrencyFormatter.format(totali.totalPagaBase)} / ${CurrencyFormatter.format(totali.totalRiscossiContanti)}"
                val formattedMancia = "${CurrencyFormatter.format(totali.totalMancia)} / ${CurrencyFormatter.format(totali.totalManciaContanti)}"
                val formattedPagaTotale = "${CurrencyFormatter.format(totali.totalPagaTotale)} / ${CurrencyFormatter.format(totali.totaleContanti)}"
                totalPagaBase.text = formattedPagaBase
                totalPagaExtra.text = CurrencyFormatter.format(totali.totalPagaExtra)
                totalPagaTotale.text = formattedPagaTotale
                totalMancia.text = formattedMancia
                totalNumeroOrdini.text = getString(R.string.total_numero_ordini_value, totali.totalNumeroOrdini)
                totalTempoImpiegato.text = strTempo
                totalPagaOraria.text = "${CurrencyFormatter.format(totali.totalPagaOraria)}/h"
            } else {
                totalPagaBase.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
                totalPagaExtra.text = CurrencyFormatter.format(0.0)
                totalMancia.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
                totalPagaTotale.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
                totalNumeroOrdini.text = getString(R.string.total_numero_ordini_value, 0)
                totalTempoImpiegato.text = convertMinutesToHoursMinutes(0)
                totalPagaOraria.text = "${CurrencyFormatter.format(0.0)}/h"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Errore durante l'aggiornamento dei totali: ${e.message}", e)
            totalPagaBase.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
            totalPagaExtra.text = CurrencyFormatter.format(0.0)
            totalMancia.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
            totalPagaTotale.text = "${CurrencyFormatter.format(0.0)} / ${CurrencyFormatter.format(0.0)}"
            totalNumeroOrdini.text = getString(R.string.total_numero_ordini_value, 0)
            totalTempoImpiegato.text = convertMinutesToHoursMinutes(0)
            totalPagaOraria.text = "${CurrencyFormatter.format(0.0)}/h"
        }
    }

    /**
     * Retrieves the ID of the latest monthly summary.
     * Dependencies: DatabaseHelper.
     */
    private fun getDefaultSummaryId(): Int {
        return dbHelper.getLatestSummaryId()
    }

    /**
     * Checks for referral codes using InstallReferrerClient and registers them in Firestore.
     * Dependencies: Firebase Firestore, InviteManager.
     */
    private fun checkInstallReferrer() {
        referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        val referrerDetails = referrerClient.installReferrer
                        val referrer = referrerDetails.installReferrer
                        val codiceAmico = "?$referrer".toUri().getQueryParameter("codiceAmico")
                        if (!codiceAmico.isNullOrEmpty()) {
                            registerInvite(codiceAmico)
                        }
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.w("MainActivity", "@string/install_referrer_feature_not_supported")
                    }
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.w("MainActivity", "@string/install_referrer_service_unavailable")
                    }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Ignorato
            }
        })
    }

    /**
     * Registers a referral code in Firestore.
     * Dependencies: Firebase Firestore, InviteManager.
     */
    private fun registerInvite(codiceAmico: String) {
        val deviceId = InviteManager.getDeviceId(this)
        Firebase.firestore.collection("invites")
            .add(
                mapOf(
                    "referrerCode" to codiceAmico,
                    "deviceId" to deviceId,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Log.d("MainActivity", getString(R.string.invite_friend_registered, codiceAmico))
            }
            .addOnFailureListener { e ->
                Log.w("MainActivity", getString(R.string.invite_friend_registration_error, e.message))
            }
    }
}

/**
 * Manages device ID generation and storage for referral tracking.
 * Dependencies: SharedPreferences ("AppPrefs").
 */
object InviteManager {
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("deviceId", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit { putString("deviceId", deviceId) }
        }
        return deviceId
    }
}