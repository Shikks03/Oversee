package com.example.oversee.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.oversee.R
import com.example.oversee.data.remote.FcmTokenManager
import com.example.oversee.ui.parent.ParentDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class OverseeMessagingService : FirebaseMessagingService() {

    private val TAG = "OverseeMessaging"
    private val CHANNEL_ID = "incident_alerts"
    private val NOTIFICATION_ID = 1001

    /**
     * Called when FCM assigns or rotates the device token.
     * Stores the new token in Firestore if the user is currently logged in.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FcmTokenManager.refreshAndStoreToken(uid)
        } else {
            // #6 — Queue token locally; AuthRepository.signIn will upload it after auth
            getSharedPreferences("AppConfig", MODE_PRIVATE)
                .edit().putString("pending_fcm_token", token).apply()
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
        val tapIntent = Intent(this, ParentDashboardActivity::class.java).apply {
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
