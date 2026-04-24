// Preview data only
package com.example.oversee.utils

import com.example.oversee.data.remote.FirebaseSyncManager

object MockData {
    fun getIncidents(): List<FirebaseSyncManager.LogEntry> {
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        // Helper function to easily space out realistic timestamps
        fun timeAgo(days: Int, hours: Int): Long {
            return now - (days * day) - (hours * hour)
        }

        return listOf(
            // --- TODAY ---
            FirebaseSyncManager.LogEntry("sc@m_link", "scam", "HIGH", "Messenger", timeAgo(0, 1)),
            FirebaseSyncManager.LogEntry("idi0t", "idiot", "MEDIUM", "Facebook", timeAgo(0, 3)),
            FirebaseSyncManager.LogEntry("b0b0", "bobo", "MEDIUM", "Instagram", timeAgo(0, 4)),
            FirebaseSyncManager.LogEntry("t@ngin@", "tangina", "HIGH", "Facebook", timeAgo(0, 6)),

            // --- 1 DAY AGO ---
            FirebaseSyncManager.LogEntry("st00pid", "stupid", "MEDIUM", "TikTok", timeAgo(1, 2)),
            FirebaseSyncManager.LogEntry("v@pe", "vape", "HIGH", "Snapchat", timeAgo(1, 5)),
            FirebaseSyncManager.LogEntry("cr@p", "crap", "LOW", "Facebook", timeAgo(1, 8)),
            FirebaseSyncManager.LogEntry("d*mb", "dumb", "LOW", "Messenger", timeAgo(1, 10)),

            // --- 2 DAYS AGO ---
            FirebaseSyncManager.LogEntry("p0rn", "porn", "HIGH", "Instagram", timeAgo(2, 1)),
            FirebaseSyncManager.LogEntry("n00des", "nudes", "HIGH", "Snapchat", timeAgo(2, 4)),
            FirebaseSyncManager.LogEntry("idiot!!", "idiot", "MEDIUM", "WhatsApp", timeAgo(2, 7)),
            FirebaseSyncManager.LogEntry("bitch", "bitch", "HIGH", "Facebook", timeAgo(2, 11)),
            FirebaseSyncManager.LogEntry("b1tch", "bitch", "HIGH", "Instagram", timeAgo(2, 12)),

            // --- 3 DAYS AGO ---
            FirebaseSyncManager.LogEntry("sh!t", "shit", "MEDIUM", "TikTok", timeAgo(3, 3)),
            FirebaseSyncManager.LogEntry("scam", "scam", "HIGH", "WhatsApp", timeAgo(3, 6)),
            FirebaseSyncManager.LogEntry("scam", "scam", "HIGH", "WhatsApp", timeAgo(3, 7)),
            FirebaseSyncManager.LogEntry("l0ser", "loser", "MEDIUM", "Facebook", timeAgo(3, 9)),

            // --- 4 DAYS AGO ---
            FirebaseSyncManager.LogEntry("f@ck", "fuck", "HIGH", "Messenger", timeAgo(4, 2)),
            FirebaseSyncManager.LogEntry("fck", "fuck", "HIGH", "Messenger", timeAgo(4, 3)),
            FirebaseSyncManager.LogEntry("stpd", "stupid", "MEDIUM", "Instagram", timeAgo(4, 8)),
            FirebaseSyncManager.LogEntry("tanginamo", "tangina", "HIGH", "Facebook", timeAgo(4, 14)),

            // --- 5 DAYS AGO ---
            FirebaseSyncManager.LogEntry("vape", "vape", "HIGH", "Snapchat", timeAgo(5, 5)),
            FirebaseSyncManager.LogEntry("vap3", "vape", "HIGH", "Snapchat", timeAgo(5, 6)),
            FirebaseSyncManager.LogEntry("crap", "crap", "LOW", "TikTok", timeAgo(5, 10)),

            // --- 6 DAYS AGO ---
            FirebaseSyncManager.LogEntry("d!ck", "dick", "HIGH", "Messenger", timeAgo(6, 1)),
            FirebaseSyncManager.LogEntry("b0b0", "bobo", "MEDIUM", "Facebook", timeAgo(6, 4)),
            FirebaseSyncManager.LogEntry("idi0t", "idiot", "MEDIUM", "WhatsApp", timeAgo(6, 7)),

            // --- 7+ DAYS AGO (To populate the 30-day overview chart) ---
            FirebaseSyncManager.LogEntry("nudes", "nudes", "HIGH", "Snapchat", timeAgo(8, 2)),
            FirebaseSyncManager.LogEntry("send_n00ds", "nudes", "HIGH", "Instagram", timeAgo(8, 3)),
            FirebaseSyncManager.LogEntry("k1ll", "kill", "HIGH", "Facebook", timeAgo(10, 5)),
            FirebaseSyncManager.LogEntry("su1c1de", "suicide", "HIGH", "TikTok", timeAgo(12, 8)),
            FirebaseSyncManager.LogEntry("l0ser", "loser", "MEDIUM", "Messenger", timeAgo(15, 2))
        )
    }
}