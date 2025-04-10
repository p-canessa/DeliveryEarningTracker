package com.piero.deliveryearningtracker

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CurrencyFormatter.initialize(this)
    }
}