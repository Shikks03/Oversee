package com.example.oversee.data.remote

import android.content.Context
import android.util.Log
import com.example.oversee.data.local.CryptoManager
import com.example.oversee.data.local.KeyManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source

/**
 * Technical implementation of Firestore operations for Incident logs.
 */
object FirebaseIncidentManager {
    private const val TAG = "FirebaseIncidentManager"
    private val db = FirebaseFirestore.getInstance()

    fun fetchIncidents(context: Context, childFid: String, onResult: (List<FirebaseSyncManager.LogEntry>?, String?) -> Unit) {
        db.collection("monitor_sessions").document(childFid).collection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get(Source.SERVER)
            .addOnSuccessListener { documents ->
                Log.d(TAG, "DIAG L3: Firestore returned ${documents.size()} raw documents for fid=$childFid")
                KeyManager.getKeyForDevice(context, childFid) { key ->
                    if (key == null) {
                        Log.e(TAG, "DIAG L3: encryption key not found for fid=$childFid — cannot decrypt")
                        onResult(null, "Encryption key not found. Make sure the child app has been set up.")
                        return@getKeyForDevice
                    }
                    val list = documents.mapNotNull { doc ->
                        try {
                            val isEncrypted = doc.getBoolean("encrypted") ?: false
                            if (isEncrypted) {
                                FirebaseSyncManager.LogEntry(
                                    word = CryptoManager.decryptString(doc.getString("word") ?: "", key),
                                    severity = CryptoManager.decryptString(doc.getString("severity") ?: "", key),
                                    app = CryptoManager.decryptString(doc.getString("app") ?: "", key),
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            } else {
                                // Legacy unencrypted document — read as plaintext
                                FirebaseSyncManager.LogEntry(
                                    word = doc.getString("word") ?: "?",
                                    severity = doc.getString("severity") ?: "LOW",
                                    app = doc.getString("app") ?: "Unknown",
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read document ${doc.id}", e)
                            null
                        }
                    }
                    Log.d(TAG, "DIAG L4: decrypted ${list.size} of ${documents.size()} documents")
                    onResult(list, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "DIAG L3: Firestore query FAILED for fid=$childFid: ${e.message}", e)
                onResult(null, e.message)
            }
    }
}