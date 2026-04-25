package com.example.oversee.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.utils.sendConsoleUpdate

class FacebookAccessibilityService : AccessibilityService() {

    private var lastFbEventTime = 0L
    private val TAG = "FacebookAccess"

    override fun onServiceConnected() {
        super.onServiceConnected()
        sendConsoleUpdate("System: Accessibility Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            ScreenCaptureService.ScreenState.isKeyboardVisible = windows?.any {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            } == true
        }

        val pkg = event?.packageName?.toString()

        if (pkg != null && (pkg.contains("facebook") || pkg.contains("katana") || pkg.contains("lite"))) {

            if (!ScreenCaptureService.ScreenState.isFacebookOpen) {
                ScreenCaptureService.ScreenState.isFacebookOpen = true
                sendConsoleUpdate("App Event: Facebook Opened")
                Log.d(TAG, "App Event: Facebook Opened")
            }

            // --- THE CONTINUOUS DRY CHECK ---
            // It checks on EVERY event inside Facebook to ensure the lock is inescapable.
            val unlockTime = AppPreferenceManager.getLong(this, "app_unlock_time", 0L)

            if (System.currentTimeMillis() < unlockTime) {
                // Priority 1: Penalty Timer
                try {
                    val intent = Intent(this, OverlayService::class.java).apply {
                        putExtra(OverlayService.EXTRA_OVERLAY_MODE, OverlayService.MODE_SEVERE_WARNING)
                    }
                    startService(intent)
                } catch (e: Exception) { Log.e(TAG, "Failed to start penalty overlay", e) }
            }
            else if (!ScreenCaptureService.CaptureState.isRunning) {
                // Priority 2: Screen Capture Permission
                try {
                    val intent = Intent(this, OverlayService::class.java).apply {
                        putExtra(OverlayService.EXTRA_OVERLAY_MODE, OverlayService.MODE_REQUIRE_MONITORING)
                    }
                    startService(intent)
                } catch (e: Exception) { Log.e(TAG, "Failed to start permission overlay", e) }
            }

            lastFbEventTime = System.currentTimeMillis()

        } else if (System.currentTimeMillis() - lastFbEventTime > 2000) {
            if (ScreenCaptureService.ScreenState.isFacebookOpen) {
                ScreenCaptureService.ScreenState.isFacebookOpen = false
                sendConsoleUpdate("App Event: Facebook Closed")

                // --- AUTO-HIDE ---
                // If they leave Facebook natively, kill the overlay so it doesn't brick their phone.
                try {
                    stopService(Intent(this, OverlayService::class.java))
                } catch (e: Exception) {}
            }
        }
    }

    override fun onInterrupt() {}
}