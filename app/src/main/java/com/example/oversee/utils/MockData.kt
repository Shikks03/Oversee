// Preview data only
package com.example.oversee.utils

import com.example.oversee.data.remote.FirebaseSyncManager

object MockData {
    fun getIncidents(): List<FirebaseSyncManager.LogEntry> {
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        return listOf(
            FirebaseSyncManager.LogEntry("scam_link", "HIGH", "Messenger", now - (2 * hour)),
            FirebaseSyncManager.LogEntry("idiot", "MEDIUM", "Facebook", now - (5 * hour)),
            FirebaseSyncManager.LogEntry("stupid", "MEDIUM", "Instagram", now - (8 * hour)),
            FirebaseSyncManager.LogEntry("dumb", "LOW", "TikTok", now - (12 * hour)),
            FirebaseSyncManager.LogEntry("crap", "LOW", "Facebook", now - (1 * day) - (4 * hour)),
            FirebaseSyncManager.LogEntry("idiot", "MEDIUM", "Messenger", now - (1 * day) - (6 * hour)),
            FirebaseSyncManager.LogEntry("scam", "HIGH", "WhatsApp", now - (3 * day)),
            FirebaseSyncManager.LogEntry("scam", "HIGH", "WhatsApp", now - (3 * day) - (1 * hour)),
            FirebaseSyncManager.LogEntry("stupid", "MEDIUM", "TikTok", now - (5 * day))
        )
    }
}