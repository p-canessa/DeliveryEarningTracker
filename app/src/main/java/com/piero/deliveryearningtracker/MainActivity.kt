package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var dateRangeSelector: DateRangeSelector
    private lateinit var totalPagaBase: TextView
    private lateinit var totalPagaExtra: TextView
    private lateinit var totalMancia: TextView
    private lateinit var totalPagaTotale: TextView
    private lateinit var totalNumeroOrdini: TextView
    private lateinit var totalTempoImpiegato: TextView
    private lateinit var totalPagaOraria: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var addOrderButton: Button
    private lateinit var addOrderButtonOCR: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sharedPref: SharedPreferences
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var adView: AdView? = null
    private var isAdsEnabled = true // Valore iniziale, aggiornato subito
    private lateinit var referrerClient: InstallReferrerClient

    // Listener per aggiornamenti dello stato degli annunci
    private val subscriptionListener: (Boolean) -> Unit = { isSubscribed ->
        Log.d("MainActivity", "Stato abbonamento aggiornato: isSubscribed=$isSubscribed")
        val newAdsEnabled = DisableAds.loadAdsEnabledState(this, dbHelper)
        if (isAdsEnabled != newAdsEnabled) {
            isAdsEnabled = newAdsEnabled
            updateAds()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val nightModeEnabled = sharedPref.getBoolean("night_mode", false)

        // Imposta il modo notte prima di caricare il layout
        AppCompatDelegate.setDefaultNightMode(
            if (nightModeEnabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // Inizializza DatabaseHelper da MyApplication
        dbHelper = (application as MyApplication).dbHelper

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "currency_symbol") {
                CurrencyFormatter.initialize(this)
                updateTotals()
                updateOrderList()
            }
        }
        sharedPref.registerOnSharedPreferenceChangeListener(preferenceListener)

        setContentView(R.layout.activity_main)
        checkInstallReferrer()

        MobileAds.initialize(this) {
            Log.d("AdMob", "Inizializzazione completata")
        }

        // Controlla lo stato degli annunci
        isAdsEnabled = DisableAds.loadAdsEnabledState(this, dbHelper)
        Log.d("MainActivity", "Stato iniziale ads_enabled: $isAdsEnabled")
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        adView = AdManager.setupBannerAd(this, adContainer, isAdsEnabled)
        if (isAdsEnabled) {
            AdManager.loadOcrAd(this)
            AdManager.loadStatinoAd(this)
        }

        // Registra il listener di BillingManager
        val billingManager = (application as MyApplication).billingManager
        billingManager.addSubscriptionListener(subscriptionListener)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isStatsEnabled = sharedPrefs.getBoolean("share_anonymous_stats", false)
        if (isStatsEnabled) {
            sendAnonymousStats(this)
            scheduleStatsUpload(this)
        }

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

        // Inizializza la Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Inizializza il DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Gestione delle selezioni del menu
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
                    drawerLayout.closeDrawer(GravityCompat.START)
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
        recyclerView = findViewById(R.id.orders_recycler_view)

        orderAdapter = OrderAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter
        recyclerView.isNestedScrollingEnabled = true
        Log.d("RecyclerDebug", "RecyclerView configurato")

        recyclerView.post {
            Log.d("RecyclerDebug", "Altezza RecyclerView: ${recyclerView.height}")
        }

        dateRangeSelector.setOnChangeListener(object : DateRangeSelector.OnChangeListener {
            override fun onChange() {
                updateTotals()
                updateOrderList()
            }
        })

        addOrderButton.setOnClickListener {
            val orderItemView = OrderItemView(this)
            orderItemView.setOnOrderSavedListener {
                updateTotals()
                updateOrderList()
            }
            orderItemView.showEditDialog()
        }

        addOrderButtonOCR.setOnClickListener {
            startActivity(Intent(this, ImageRecognitionActivity::class.java))
        }

        orderAdapter.setOnOrderDeletedListener {
            updateTotals()
            updateOrderList()
        }

        orderAdapter.setOnOrderSavedListener {
            updateTotals()
            updateOrderList()
        }

        updateTotals()
        updateOrderList()
    }

    override fun onResume() {
        super.onResume()
        updateTotals()
        updateOrderList()
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy chiamato")
        AdManager.destroyBannerAd(adView)
        adView = null
        val billingManager = (application as MyApplication).billingManager
        billingManager.removeSubscriptionListener(subscriptionListener)
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        referrerClient.endConnection()
        super.onDestroy()
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

    private fun updateAds() {
        Log.d("MainActivity", "Aggiornamento annunci: isAdsEnabled=$isAdsEnabled")
        val adContainer = findViewById<LinearLayout>(R.id.ad_container)
        if (isAdsEnabled) {
            adView = AdManager.setupBannerAd(this, adContainer, true)
            AdManager.loadOcrAd(this)
            AdManager.loadStatinoAd(this)
        } else {
            AdManager.destroyBannerAd(adView)
            adView = null
            adContainer.removeAllViews()
        }
    }

    private fun updateOrderList() {
        try {
            val sqlClause = dateRangeSelector.getSqlClause("Data")
            Log.d("RecyclerDebug", "Clausola SQL: $sqlClause")
            val orderIds = dbHelper.getOrderIds(sqlClause)
            Log.d("RecyclerDebug", "ID trovati: $orderIds")
            orderAdapter.updateOrders(orderIds)
        } catch (e: Exception) {
            Log.e("MainActivity", "Errore durante l'aggiornamento della lista ordini: ${e.message}", e)
            orderAdapter.updateOrders(emptyList())
        }
    }

    private fun convertMinutesToHoursMinutes(minutes: Int): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (minutes > 60) getString(R.string.time_format_hours_minutes, hours, remainingMinutes)
        else getString(R.string.time_format_minutes, minutes)
    }

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

    private fun getDefaultSummaryId(): Int {
        return dbHelper.getLatestSummaryId()
    }

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