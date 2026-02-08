package com.example.prototype.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Technical implementation of Firestore operations for Incident logs.
 */
object FirebaseIncidentManager {
    private val db = FirebaseFirestore.getInstance()

    fun fetchIncidents(childId: String, onResult: (List<FirebaseSyncManager.LogEntry>?, String?) -> Unit) {
        db.collection("monitor_sessions").document(childId).collection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val list = documents.map { doc ->
                    FirebaseSyncManager.LogEntry(
                        word = doc.getString("word") ?: "?",
                        severity = doc.getString("severity") ?: "LOW",
                        app = doc.getString("app") ?: "Unknown",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
                onResult(list, null)
            }
            .addOnFailureListener { onResult(null, it.message) }
    }
}