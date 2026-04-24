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
        // Fetch the buckets, ordering by document ID (which is the date string) so we get newest first
        db.collection("monitor_sessions").document(childFid).collection("logs")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(3) // Only download the last 3 months of buckets to save data
            .get(Source.SERVER)
            .addOnSuccessListener { documents ->
                KeyManager.getKeyForDevice(context, childFid) { key ->
                    if (key == null) {
                        onResult(null, "Encryption key not found.")
                        return@getKeyForDevice
                    }

                    val allLogs = mutableListOf<FirebaseSyncManager.LogEntry>()

                    // 1. Loop through the downloaded buckets (e.g., April, March, February)
                    for (doc in documents) {
                        // Extract the big array from the bucket
                        val logsArray = doc.get("logsArray") as? List<Map<String, Any>> ?: continue

                        // 2. Loop through the individual logs inside the array
                        for (item in logsArray) {
                            try {
                                val isEncrypted = item["encrypted"] as? Boolean ?: false
                                if (isEncrypted) {
                                    allLogs.add(
                                        FirebaseSyncManager.LogEntry(
                                            rawWord = CryptoManager.decryptString(item["rawWord"] as String? ?: "", key),
                                            matchedWord = CryptoManager.decryptString(item["matchedWord"] as String? ?: "", key),
                                            severity = CryptoManager.decryptString(item["severity"] as String? ?: "", key),
                                            app = CryptoManager.decryptString(item["app"] as String? ?: "", key),
                                            timestamp = item["timestamp"] as? Long ?: 0L
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to decrypt an item in bucket ${doc.id}", e)
                            }
                        }
                    }

                    // 3. Sort the combined list so the newest items are at the top
                    allLogs.sortByDescending { it.timestamp }

                    // Send the data to the UI!
                    onResult(allLogs, null)
                }
            }
            .addOnFailureListener { e ->
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