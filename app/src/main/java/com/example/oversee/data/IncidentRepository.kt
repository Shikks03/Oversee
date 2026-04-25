package com.example.oversee.data

import android.content.Context
import android.util.Log
import com.example.oversee.data.local.LocalStorageManager
import com.example.oversee.data.model.Incident
import com.example.oversee.data.remote.FcmAlertManager
import com.example.oversee.data.remote.FirebaseIncidentManager
import com.example.oversee.data.remote.FirebaseSyncManager

/**
 * Coordinates incident logging and data fetching.
 */
object IncidentRepository {

    private const val TAG = "IncidentRepository"
    // Save logic for Child device
    fun saveIncident(context: Context, incident: Incident) {
        LocalStorageManager.logIncident(context, incident.rawWord, incident.matchedWord, incident.severity, incident.appName)

        if (incident.severity == "HIGH") {
            Log.d(TAG, "🚨 High-Risk Incident Logged! Initiating Emergency Cloud Sync...")
            FirebaseSyncManager.syncPendingLogs(context)

            Log.d(TAG, "🚨 Pushing Emergency Alert Notification to Parent Device...")
            FcmAlertManager.sendHighSeverityAlert(context)
        }
    }

    // Fetch logic for Parent device
    fun fetchRecentIncidents(
        context: Context,
        childFid: String,
        onSuccess: (List<FirebaseSyncManager.LogEntry>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (childFid.isBlank()) {
            onSuccess(emptyList())
            return
        }

        FirebaseIncidentManager.fetchIncidents(context, childFid) { list, error ->
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