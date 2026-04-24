package com.example.oversee.data.remote

import android.content.Context
import android.util.Log
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.local.CryptoManager
import com.example.oversee.data.local.KeyManager
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File
import androidx.core.content.edit

/**
 * Handles synchronization between Local Storage and Firebase Firestore.
 *
 * Logic:
 * 1. Reads local logs.
 * 2. Filters out logs that have already been uploaded (using 'last_sync_time').
 * 3. Batches new logs into a Firestore Sub-collection.
 */
object FirebaseSyncManager {

    // --- CONSTANTS ---
    private const val TAG = "FirebaseSync"
    private const val PREFS_NAME = "SyncPrefs"
    private const val KEY_LAST_SYNC = "last_sync_time"
    private const val COLLECTION_SESSIONS = "monitor_sessions"
    private const val SUBCOL_LOGS = "logs"

    // --- PUBLIC API ---

    fun syncPendingLogs(context: Context, onDone: ((uploaded: Int, error: String?) -> Unit)? = null) {
        val lastSyncTime = getLastSyncTime(context)
        val allLogs = parseLocalLogs(context)

        val newLogs = allLogs.filter { it.timestamp > lastSyncTime }

        if (newLogs.isEmpty()) {
            Log.d(TAG, "☁️ Sync skipped: No new logs.")
            onDone?.invoke(0, null)
            return
        }

        Log.d(TAG, "☁️ Syncing ${newLogs.size} incidents to Cloud...")
        DeviceRepository.getFid { fid ->
            if (fid == null) {
                Log.w(TAG, "FID unavailable, skipping sync")
                onDone?.invoke(0, "Device ID unavailable")
                return@getFid
            }
            uploadBatch(context, newLogs, fid, onDone)
        }
    }

    // --- PRIVATE HELPERS ---

    private fun uploadBatch(context: Context, logs: List<LogEntry>, fid: String, onDone: ((uploaded: Int, error: String?) -> Unit)? = null) {
        KeyManager.getOrCreateKey(context, fid) { key ->
            val db = FirebaseFirestore.getInstance()

            val userDocRef = db.collection(COLLECTION_SESSIONS).document(fid)
            val batch = db.batch()

            // Create a new document in the "logs" sub-collection for each incident
            for (log in logs) {
                val newDocRef = userDocRef.collection(SUBCOL_LOGS).document()

                val dataMap = hashMapOf(
                    "word" to CryptoManager.encryptString(log.word, key),
                    "severity" to CryptoManager.encryptString(log.severity, key),
                    "app" to CryptoManager.encryptString(log.app, key),
                    "timestamp" to log.timestamp,  // Left unencrypted for Firestore ordering
                    "encrypted" to true
                )

                batch.set(newDocRef, dataMap)
            }

            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Cloud Sync Complete!")
                    saveLastSyncTime(context, System.currentTimeMillis())
                    onDone?.invoke(logs.size, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Cloud Sync Failed", e)
                    onDone?.invoke(0, e.message ?: "Upload failed")
                }
        }
    }

    // --- FILE PARSING ---

    data class LogEntry(
        val word: String,
        val severity: String,
        val app: String,
        val timestamp: Long
    )

    private fun parseLocalLogs(context: Context): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        val file = File(context.filesDir, "incidents_log.json")
        if (!file.exists()) return entries

        try {
            file.readLines().forEach { line ->
                val trimmed = line.trim().removeSuffix(",")
                if (trimmed.isNotEmpty()) {
                    try {
                        val obj = JSONObject(trimmed)
                        entries.add(LogEntry(
                            word = obj.getString("word"),
                            severity = obj.getString("severity"),
                            app = obj.getString("app"),
                            timestamp = obj.optLong("timestamp", 0L)
                        ))
                    } catch (e: Exception) {
                        // Skip corrupted lines
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing local logs", e)
        }
        return entries
    }

    // --- PREFERENCES MANAGEMENT ---

    private fun getLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)
    }

    private fun saveLastSyncTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_LAST_SYNC, time)
            }
    }
}