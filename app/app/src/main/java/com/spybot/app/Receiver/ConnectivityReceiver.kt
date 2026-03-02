package com.spybot.app.Receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.spybot.app.MainService

class ConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            "android.net.conn.CONNECTIVITY_CHANGE",
            "android.net.wifi.WIFI_STATE_CHANGED",
            Intent.ACTION_USER_PRESENT -> {
                if (isNetworkAvailable(context)) {
                    ensureServiceRunning(context)
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun ensureServiceRunning(context: Context) {
        if (!shouldServiceRun(context)) return

        try {
            val serviceIntent = Intent(context, MainService::class.java).apply { action = MainService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            MainService.scheduleRestartAlarm(context, 3000)
        }
    }

    private fun shouldServiceRun(context: Context): Boolean {
        return context.getSharedPreferences("spybot_prefs", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", true)
    }
}
