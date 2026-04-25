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
            .limit(1000)
            .get()
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
                                // Fallback for old logs that only used "word"
                                val rawEncrypted = doc.getString("rawWord") ?: doc.getString("word") ?: ""
                                val matchedEncrypted = doc.getString("matchedWord") ?: doc.getString("word") ?: ""

                                FirebaseSyncManager.LogEntry(
                                    rawWord = CryptoManager.decryptString(rawEncrypted, key),
                                    matchedWord = CryptoManager.decryptString(matchedEncrypted, key),
                                    severity = CryptoManager.decryptString(doc.getString("severity") ?: "", key),
                                    app = CryptoManager.decryptString(doc.getString("app") ?: "", key),
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            } else {
                                val legacyWord = doc.getString("word") ?: "?"
                                FirebaseSyncManager.LogEntry(
                                    rawWord = doc.getString("rawWord") ?: legacyWord,
                                    matchedWord = doc.getString("matchedWord") ?: legacyWord,
                                    severity = doc.getString("severity") ?: "LOW",
                                    app = doc.getString("app") ?: "Unknown",
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            }
                        } catch (e: Exception) { null }
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

    /**
     * Deletes the old child's encrypted logs and encryption key from the database
     * to prevent "Ghost Data" and save server storage.
     */
    fun deleteOldChildData(oldFid: String) {
        val oldSessionRef = db.collection("monitor_sessions").document(oldFid)

        // Find all the old logs and delete them in a batch
        oldSessionRef.collection("logs").get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().addOnSuccessListener {
                // Once the logs are gone, delete the master document containing the encryption key
                oldSessionRef.delete()
                Log.d(TAG, "Successfully wiped old child data for FID: $oldFid")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to delete old child data", e)
        }
    }
}