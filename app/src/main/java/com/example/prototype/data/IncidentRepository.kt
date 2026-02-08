package com.example.prototype.data

import android.content.Context
import com.example.prototype.data.local.LocalStorageManager
import com.example.prototype.data.model.Incident
import com.example.prototype.data.remote.FirebaseIncidentManager
import com.example.prototype.data.remote.FirebaseSyncManager

/**
 * Coordinates incident logging and data fetching.
 */
object IncidentRepository {

    // Save logic for Child device
    fun saveIncident(context: Context, incident: Incident) {
        LocalStorageManager.logIncident(context, incident.word, incident.severity, incident.appName)

        // Critical alerts sync immediately
        if (incident.severity == "HIGH") {
            FirebaseSyncManager.syncPendingLogs(context)
        }
    }

    // Fetch logic for Parent device
    fun fetchRecentIncidents(
        childId: String,
        onSuccess: (List<FirebaseSyncManager.LogEntry>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (childId == "NOT_LINKED" || childId.isBlank()) {
            onSuccess(emptyList())
            return
        }

        FirebaseIncidentManager.fetchIncidents(childId) { list, error ->
            if (list != null) onSuccess(list)
            else onError(error ?: "Unknown error")
        }
    }

    /**
     * Manually triggers a synchronization of all pending local logs to the cloud.
     * Usually called by a periodic background timer or user action.
     */
    fun syncData(context: Context) {
        FirebaseSyncManager.syncPendingLogs(context)
    }
}