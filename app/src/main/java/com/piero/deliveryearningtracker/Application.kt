package com.piero.deliveryearningtracker

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    lateinit var dbHelper: DatabaseHelper
        private set
    lateinit var billingManager: BillingManager
        private set


    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "Inizializzazione applicazione")

        CurrencyFormatter.initialize(this)
        dbHelper = DatabaseHelper(this)
        dbHelper.initializeDatabase()
        billingManager = BillingManager.getInstance(this, dbHelper)
    }
}