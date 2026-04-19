package com.example.oversee.data.local

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages reading and writing incidents to the device's internal storage.
 *
 * Implements a "Cooldown/Debounce" mechanism to prevent flooding the logs
 * if a user stares at the same bad word for 10 seconds.
 */
object LocalStorageManager {

    // --- CONSTANTS ---
    private const val TAG = "LocalStorageManager"
    private const val FILE_NAME = "incidents_log.json"

    // Config: A word must be off-screen for 10 seconds before we log it again.
    private const val COOLDOWN_MS = 10_000L

    // --- STATE ---
    // Thread-safe map to track the last seen timestamp of each word.
    private val lastLogTimeMap = ConcurrentHashMap<String, Long>()

    // --- PUBLIC API ---

    fun logIncident(context: Context, word: String, severity: String, appName: String) {
        val currentTime = System.currentTimeMillis()

        // 1. Cooldown Check
        if (shouldSkipLog(word, currentTime)) {
            Log.d(TAG, "⏳ DEBOUNCED: '$word' (Seen recently)")
            return
        }

        // 2. Persist Data
        saveToFile(context, word, severity, appName, currentTime)
    }

    /**
     * Reads the raw JSON log file.
     * Used mainly for debugging or if the sync manager needs to re-read history.
     */
    fun getAllLogs(context: Context): String {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readText() else "No logs yet."
        } catch (e: Exception) {
            "Error reading logs."
        }
    }

    // --- PRIVATE HELPERS ---

    private fun shouldSkipLog(word: String, currentTime: Long): Boolean {
        val lastTime = lastLogTimeMap[word] ?: 0L
        val timeDiff = currentTime - lastTime

        // Update the timestamp to "now" so the timer resets
        lastLogTimeMap[word] = currentTime

        // Return true if the time difference is LESS than our cooldown
        return timeDiff < COOLDOWN_MS
    }

    private fun saveToFile(context: Context, word: String, severity: String, app: String, time: Long) {
        try {
            val jsonObject = JSONObject().apply {
                put("word", word)
                put("severity", severity)
                put("app", app)
                put("timestamp", time)
                put("readable_time", formatTime(time))
            }

            // Append newline for "Line-Delimited JSON" format
            val entry = "$jsonObject,\n"

            context.openFileOutput(FILE_NAME, Context.MODE_APPEND).use { stream ->
                stream.write(entry.toByteArray())
            }

            Log.d(TAG, "✅ SAVED: $entry")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to internal storage", e)
        }
    }

    private fun formatTime(time: Long): String {
        return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
    }
}