package com.example.oversee.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.oversee.data.PunishmentRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.utils.sendConsoleUpdate

class FacebookAccessibilityService : AccessibilityService() {

    private var lastEventTime = 0L // Renamed for generic use
    private val TAG = "AppMonitorAccess"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AppPreferenceManager.getBoolean(this, "monitoring_enabled", true)) return

        val eventType = event?.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            ScreenCaptureService.ScreenState.isKeyboardVisible = windows?.any {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            } == true
        }

        val pkg = event?.packageName?.toString() ?: ""

        // --- NEW DETECTION LOGIC ---
        // 1. Monitor Facebook, but EXCLUDE Messenger (com.facebook.orca)
        val isFacebook = pkg.contains("facebook") && !pkg.contains("orca")
        // 2. Add Instagram
        val isInstagram = pkg.contains("instagram")

        if (isFacebook || isInstagram) {
            val appName = if (isInstagram) "Instagram" else "Facebook"

            // If switching apps or opening for the first time
            if (!ScreenCaptureService.ScreenState.isAppOpen || ScreenCaptureService.ScreenState.currentAppName != appName) {
                ScreenCaptureService.ScreenState.isAppOpen = true
                ScreenCaptureService.ScreenState.currentAppName = appName
                sendConsoleUpdate("App Event: $appName Opened")
                Log.d(TAG, "App Event: $appName Opened")
            }

            val unlockTime = AppPreferenceManager.getLong(this, "app_unlock_time", 0L)
            val punishmentStatus = AppPreferenceManager.getString(this, "punishment_status", PunishmentRepository.STATUS_NONE)
            val punishmentActive = punishmentStatus == PunishmentRepository.STATUS_ACTIVE ||
                punishmentStatus == PunishmentRepository.STATUS_PENDING_APPROVAL

            if (System.currentTimeMillis() < unlockTime || punishmentActive) {
                try {
                    val intent = Intent(this, OverlayService::class.java).apply {
                        putExtra(OverlayService.EXTRA_OVERLAY_MODE, OverlayService.MODE_SEVERE_WARNING)
                    }
                    startService(intent)
                } catch (e: Exception) { Log.e(TAG, "Failed to start penalty overlay", e) }
            }
            else if (!ScreenCaptureService.CaptureState.isRunning) {
                try {
                    val intent = Intent(this, OverlayService::class.java).apply {
                        putExtra(OverlayService.EXTRA_OVERLAY_MODE, OverlayService.MODE_REQUIRE_MONITORING)
                    }
                    startService(intent)
                } catch (e: Exception) { Log.e(TAG, "Failed to start permission overlay", e) }
            }

            lastEventTime = System.currentTimeMillis()

        } else if (System.currentTimeMillis() - lastEventTime > 2000) {
            // The penalty overlay grabs focus, so we briefly see non-monitored window events.
            // Never tear the overlay down while a timeout or punishment is still in effect —
            // doing so makes it flicker and resets the chore checklist.
            val unlockTime = AppPreferenceManager.getLong(this, "app_unlock_time", 0L)
            val status = AppPreferenceManager.getString(this, "punishment_status", PunishmentRepository.STATUS_NONE)
            val penaltyActive = System.currentTimeMillis() < unlockTime ||
                status == PunishmentRepository.STATUS_ACTIVE ||
                status == PunishmentRepository.STATUS_PENDING_APPROVAL

            // Only close if we haven't seen a monitored app event in 2 seconds
            if (ScreenCaptureService.ScreenState.isAppOpen && !penaltyActive) {
                ScreenCaptureService.ScreenState.isAppOpen = false
                sendConsoleUpdate("App Event: Monitored App Closed")

                try {
                    stopService(Intent(this, OverlayService::class.java))
                } catch (e: Exception) {}
            }
        }
    }

    override fun onInterrupt() {}
}