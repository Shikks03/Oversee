package com.example.oversee.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.oversee.service.ScreenCaptureService
import com.google.firebase.FirebaseApp

object SystemHealthManager {
    fun isAccessibilityOn(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun isNotificationOn(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isOverlayOn(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isInternetOn(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        @Suppress("DEPRECATION") return cm.activeNetworkInfo?.isConnected == true
    }

    fun isFirebaseReady(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun isScreenCaptureActive(): Boolean = ScreenCaptureService.CaptureState.isRunning

    fun navigateToSetting(context: Context, label: String) {
        val intent = when (label) {
            "Accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "Notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }
            "Overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            "Capture" -> Intent(context, com.example.oversee.ui.child.CapturePermissionActivity::class.java).apply {
                // Required when starting an Activity from outside a standard Activity context
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            "Internet" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            else -> null
        }

        if (intent != null) {
            context.startActivity(intent)
        } else if (label == "Firebase") {
            // Firebase is an internal app state, not an Android system setting.
            android.widget.Toast.makeText(context, "Checking Firebase connection...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}