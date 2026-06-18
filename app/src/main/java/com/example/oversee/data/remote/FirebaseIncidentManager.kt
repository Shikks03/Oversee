package com.example.oversee.data.remote

import android.content.Context
import android.util.Log
import com.example.oversee.data.local.CryptoManager
import com.example.oversee.data.local.KeyManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import javax.crypto.SecretKey

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
                    decryptBuckets(context, childFid, documents, key, allowRefresh = true, onResult)
                }
            }
            .addOnFailureListener { e ->
                onResult(null, e.message)
            }
    }

    /**
     * Decrypts every bucket with [key]. If the key decrypts nothing while encrypted
     * items exist, the cached key is stale (the child rotated it) — so we refresh the
     * key from Firestore once and retry. [allowRefresh] guards against an infinite loop.
     */
    private fun decryptBuckets(
        context: Context,
        childFid: String,
        documents: Iterable<com.google.firebase.firestore.QueryDocumentSnapshot>,
        key: SecretKey,
        allowRefresh: Boolean,
        onResult: (List<FirebaseSyncManager.LogEntry>?, String?) -> Unit
    ) {
        val allLogs = mutableListOf<FirebaseSyncManager.LogEntry>()
        var encryptedCount = 0
        var failureCount = 0

        // 1. Loop through the downloaded buckets (e.g., April, March, February)
        for (doc in documents) {
            // Extract the big array from the bucket
            @Suppress("UNCHECKED_CAST")
            val logsArray = doc.get("logsArray") as? List<Map<String, Any>> ?: continue

            // 2. Loop through the individual logs inside the array
            for (item in logsArray) {
                val isEncrypted = item["encrypted"] as? Boolean ?: false
                if (!isEncrypted) continue
                encryptedCount++
                try {
                    allLogs.add(
                        FirebaseSyncManager.LogEntry(
                            rawWord = CryptoManager.decryptString(item["rawWord"] as String? ?: "", key),
                            matchedWord = CryptoManager.decryptString(item["matchedWord"] as String? ?: "", key),
                            severity = CryptoManager.decryptString(item["severity"] as String? ?: "", key),
                            app = CryptoManager.decryptString(item["app"] as String? ?: "", key),
                            timestamp = (item["timestamp"] as? Number)?.toLong() ?: 0L
                        )
                    )
                } catch (e: Exception) {
                    failureCount++
                }
            }
        }

        // Stale-key recovery: the cached key decrypted nothing but encrypted data exists.
        // The child has rotated its key — refresh from Firestore and retry once.
        if (allowRefresh && encryptedCount > 0 && allLogs.isEmpty()) {
            Log.w(TAG, "All $encryptedCount encrypted item(s) failed to decrypt for childFid=$childFid; refreshing key and retrying")
            KeyManager.refreshKeyForDevice(context, childFid) { freshKey ->
                if (freshKey == null || freshKey.encoded.contentEquals(key.encoded)) {
                    // No fresher key is available; the data is encrypted with a key
                    // that no longer exists. Return empty rather than spinning.
                    Log.e(TAG, "No newer key available for childFid=$childFid; $encryptedCount item(s) are unrecoverable")
                    onResult(emptyList(), null)
                } else {
                    decryptBuckets(context, childFid, documents, freshKey, allowRefresh = false, onResult)
                }
            }
            return
        }

        if (failureCount > 0) {
            Log.e(TAG, "Skipped $failureCount undecryptable item(s) for childFid=$childFid (decrypted ${allLogs.size})")
        }

        // 3. Sort the combined list so the newest items are at the top
        allLogs.sortByDescending { it.timestamp }

        // Send the data to the UI!
        onResult(allLogs, null)
    }

    /**
     * Deletes the old child's encrypted logs and encryption key from the database
     * to prevent "Ghost Data" and save server storage.
     */
    fun deleteOldChildData(oldFid: String, onComplete: (Boolean) -> Unit = {}) {
        val oldSessionRef = db.collection("monitor_sessions").document(oldFid)

        oldSessionRef.collection("logs").get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().addOnSuccessListener {
                oldSessionRef.delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully wiped old child data for FID: $oldFid")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete session doc for FID: $oldFid", e)
                        onComplete(false)
                    }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete old log buckets", e)
                onComplete(false)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to delete old child data", e)
            onComplete(false)
        }
    }
}