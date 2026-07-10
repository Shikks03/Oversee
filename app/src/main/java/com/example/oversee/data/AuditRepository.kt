// oversee/data/AuditRepository.kt
package com.example.oversee.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AuditRepository {

    data class AuditEvent(
        val id: String = "",
        val action: String = "",
        val details: String = "",
        val timestamp: Long = 0L,
        val device: String = ""
    )

    /**
     * Saves an event locally using SharedPreferences and JSON.
     */
    fun logEvent(context: Context, action: String, details: String) {
        val prefs = context.getSharedPreferences("local_audit_logs", Context.MODE_PRIVATE)

        android.widget.Toast.makeText(context, "Audit: $action", android.widget.Toast.LENGTH_SHORT).show()

        val currentLogsJson = prefs.getString("logs_array", "[]") ?: "[]"

        val array = JSONArray(currentLogsJson)
        val newEvent = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("action", action)
            put("details", details)
            put("timestamp", System.currentTimeMillis())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
        array.put(newEvent)

        prefs.edit().putString("logs_array", array.toString()).apply()
    }

    /**
     * Fetches all local logs chronologically (newest first).
     */
    fun fetchLogs(context: Context, onResult: (List<AuditEvent>) -> Unit) {
        val prefs = context.getSharedPreferences("local_audit_logs", Context.MODE_PRIVATE)
        val currentLogsJson = prefs.getString("logs_array", "[]") ?: "[]"
        val array = JSONArray(currentLogsJson)

        val logs = mutableListOf<AuditEvent>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            logs.add(
                AuditEvent(
                    id = obj.optString("id"),
                    action = obj.optString("action"),
                    details = obj.optString("details"),
                    timestamp = obj.optLong("timestamp"),
                    device = obj.optString("device")
                )
            )
        }

        // Sort descending so the newest event is at the top of the screen
        onResult(logs.sortedByDescending { it.timestamp })
    }
}