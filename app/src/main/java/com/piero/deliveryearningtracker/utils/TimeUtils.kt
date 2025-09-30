package com.piero.deliveryearningtracker.utils

import android.util.Log
import java.sql.Time
import java.util.Calendar

object TimeUtils {
    fun timeToMinutes(time: String): Int {
        val parts = time.split(":", ";")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        return hours * 60 + minutes
    }
    fun timeToMinutes(time: Time): Int {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = time.time
        }
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        return hours * 60 + minutes
    }
    fun minutesToHours(minutes: Int): Double {
        return minutes / 60.0
    }

    fun calculateTimeDifference(startTime: Time, endTime: Time): Int {
        Log.d("TimeUtils", "Start time: $startTime\nEnd time: $endTime")
        val startMinutes = timeToMinutes(startTime)
        var endMinutes = timeToMinutes(endTime)
        Log.d("TimeUtils", "Start time in minutes: $startMinutes\nEnd time in minutes: $endMinutes")
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60
        }
        val timeDifference = (endMinutes - startMinutes)
        Log.d("TimeUtils", "Time difference in minutes: $timeDifference")
        return timeDifference
    }

    fun calculateTimeDifference(startMinutes: Int, endMinutes: Int): Int {
        var adjustedEndMinutes = endMinutes
        if (adjustedEndMinutes < startMinutes) {
            adjustedEndMinutes += 24 * 60 // Correzione per mezzanotte
        }
        return (adjustedEndMinutes - startMinutes)
    }
}