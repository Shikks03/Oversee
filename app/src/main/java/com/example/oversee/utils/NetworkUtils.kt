package com.example.oversee.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

object NetworkUtils {

    fun isAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Starts a timeout timer. Returns a cancel function — call it when the operation
     * completes normally so the timeout does not fire.
     */
    fun startTimeout(timeoutMs: Long = 15_000L, onTimeout: () -> Unit): () -> Unit {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val runnable = Runnable {
            if (!done) {
                done = true
                onTimeout()
            }
        }
        handler.postDelayed(runnable, timeoutMs)
        return {
            if (!done) {
                done = true
                handler.removeCallbacks(runnable)
            }
        }
    }
}
