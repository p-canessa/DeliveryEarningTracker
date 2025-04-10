package com.piero.deliveryearningtracker.workers

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.piero.deliveryearningtracker.utils.sendAnonymousStats // Importa da AnalyticsUtils

class StatsUploadWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val isEnabled = sharedPrefs.getBoolean("share_anonymous_stats", false)

        if (isEnabled) {
            sendAnonymousStats(applicationContext)
            return Result.success()
        }
        return Result.success()
    }
}