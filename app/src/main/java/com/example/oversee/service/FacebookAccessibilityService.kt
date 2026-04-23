package com.example.oversee.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.oversee.utils.sendConsoleUpdate

class FacebookAccessibilityService : AccessibilityService() {

    private var lastFbEventTime = 0L
    private val TAG = "FacebookAccess"

    override fun onServiceConnected() {
        super.onServiceConnected()
        sendConsoleUpdate("System: Accessibility Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Step 5: update keyboard visibility on every window-state change.
        // Accessibility window list is authoritative and handles floating/split-screen keyboards.
        val eventType = event?.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            ScreenCaptureService.ScreenState.isKeyboardVisible = windows?.any {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            } == true
        }

        val pkg = event?.packageName?.toString()

        if (pkg != null && (pkg.contains("facebook") || pkg.contains("katana") || pkg.contains("lite"))) {

            // Only update/log if state CHANGED to True
            if (!ScreenCaptureService.ScreenState.isFacebookOpen) {
                ScreenCaptureService.ScreenState.isFacebookOpen = true
                sendConsoleUpdate("App Event: Facebook Opened")
                Log.d(TAG, "App Event: Facebook Opened")

                if (!ScreenCaptureService.CaptureState.isRunning) {
                    try {
                        val intent = Intent(this, OverlayService::class.java)
                        startService(intent)
                        Log.d(TAG, "Blocking Facebook: Capture not active")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start overlay", e)
                    }
                }
            }
            lastFbEventTime = System.currentTimeMillis()

        } else if (System.currentTimeMillis() - lastFbEventTime > 2000) {
            // Timeout Logic: If no FB event for 2 seconds, assume we left
            if (ScreenCaptureService.ScreenState.isFacebookOpen) {
                ScreenCaptureService.ScreenState.isFacebookOpen = false
                sendConsoleUpdate("App Event: Facebook Closed")
            }
        }
    }

    override fun onInterrupt() {}
}