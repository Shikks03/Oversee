package com.example.oversee.data.remote

import android.content.Context
import com.example.oversee.data.local.CryptoManager
import com.example.oversee.data.local.KeyManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Technical implementation of Firestore operations for Incident logs.
 */
object FirebaseIncidentManager {
    private val db = FirebaseFirestore.getInstance()

    fun fetchIncidents(context: Context, childFid: String, onResult: (List<FirebaseSyncManager.LogEntry>?, String?) -> Unit) {
        db.collection("monitor_sessions").document(childFid).collection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                KeyManager.getOrCreateKey(context, childFid) { key ->
                    val list = documents.map { doc ->
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
                    }
                    onResult(list, null)
                }
            }
            .addOnFailureListener { onResult(null, it.message) }
    }
}