package com.example.oversee.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.oversee.R
import com.example.oversee.data.DeviceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.core.content.edit

class OverseeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "OverseeMessaging"
        private const val CHANNEL_ID = "incident_alerts"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            DeviceRepository.getFid { fid ->
                if (fid != null) {
                    DeviceRepository.refreshFcmToken(uid, fid, token) { success ->
                        if (!success) Log.w(TAG, "Failed to store rotated FCM token")
                    }
                } else {
                    Log.w(TAG, "Could not get FID to store rotated FCM token")
                }
            }
        } else {
            getSharedPreferences("AppConfig", MODE_PRIVATE)
                .edit { putString("pending_fcm_token", token) }
            Log.d(TAG, "User not logged in, FCM token queued for next sign-in")
        }
    }

    /**
     * Called when a data message is received from FCM.
     * Builds and shows a local high-priority notification for the parent.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"] ?: return
        if (type != "HIGH_SEVERITY_INCIDENT") return

        Log.d(TAG, "High severity alert received from FCM")
        createNotificationChannel()
        showIncidentNotification()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incident Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for high severity incidents detected on the child device"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showIncidentNotification() {
        // CHANGED: Point the intent to MainActivity instead
        val tapIntent = Intent(this, com.example.oversee.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_refresh", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("High Severity Alert")
            .setContentText("A high severity word has been detected on the child's device.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }
}
