package com.spybot.app.Utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object AppScope {
    private val executor = Executors.newCachedThreadPool()

    fun runBack(runnable: Runnable) {
        executor.execute(runnable)
    }

    fun runMain(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }
}
