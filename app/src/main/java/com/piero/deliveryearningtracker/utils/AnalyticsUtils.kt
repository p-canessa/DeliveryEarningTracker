package com.piero.deliveryearningtracker.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.piero.deliveryearningtracker.DatabaseHelper
import com.piero.deliveryearningtracker.R
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.piero.deliveryearningtracker.workers.StatsUploadWorker
import java.util.concurrent.TimeUnit


data class AnonymousStats(
    val newOrders: Int,
    val averageHourlyPay: Double,
    val newOcrScans: Int
)

fun collectAnonymousStats(context: Context): AnonymousStats {
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    val lastSentId = sharedPrefs.getInt("last_sent_id", 0)
    val lastOcrScansSent = sharedPrefs.getInt("last_ocr_scans_sent", 0)
    val currentOcrScans = sharedPrefs.getInt("ocr_scan_count", 0)

    val dbHelper = DatabaseHelper(context)
    val db = dbHelper.readableDatabase
    val query = context.getString(R.string.Get_Orders_Since_Id)
    val cursor = db.rawQuery(query, arrayOf(lastSentId.toString()))

    var newOrdersCount = 0
    var totalHourlyPay = 0.0
    var maxId = lastSentId

    if (cursor.moveToFirst()) {
        do {
            val numeroOrdini = cursor.getInt(cursor.getColumnIndexOrThrow("NumeroOrdini"))
            val pagaOraria = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaOraria"))
            newOrdersCount += numeroOrdini
            totalHourlyPay += pagaOraria * numeroOrdini

            val currentId = cursor.getLong(cursor.getColumnIndexOrThrow("ID"))
            if (currentId > maxId) maxId = currentId.toInt()
        } while (cursor.moveToNext())
    }
    cursor.close()

    val averageHourlyPay = if (newOrdersCount > 0) totalHourlyPay / newOrdersCount else 0.0
    val newOcrScans = currentOcrScans - lastOcrScansSent

    sharedPrefs.edit {
        putInt("last_sent_id", maxId)
            .putInt("last_ocr_scans_sent", currentOcrScans)
    }

    return AnonymousStats(newOrdersCount, averageHourlyPay, newOcrScans.coerceAtLeast(0))
}

fun sendAnonymousStats(context: Context) {
    val stats = collectAnonymousStats(context)
    val analytics = FirebaseAnalytics.getInstance(context)

    val bundle = Bundle().apply {
        putInt("new_orders", stats.newOrders)
        putDouble("average_hourly_pay", stats.averageHourlyPay)
        putInt("new_ocr_scans", stats.newOcrScans)
    }
    analytics.logEvent("anonymous_stats", bundle)
    Log.d("Stats", "Statistiche incrementali inviate: $stats")
}

fun incrementOcrScanCount(context: Context, scans: Int = 1) {
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    val currentCount = sharedPrefs.getInt("ocr_scan_count", 0)
    sharedPrefs.edit { putInt("ocr_scan_count", currentCount + scans) }
}

fun scheduleStatsUpload(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<StatsUploadWorker>(24, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("stats_upload", ExistingPeriodicWorkPolicy.KEEP, workRequest)
}