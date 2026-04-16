package com.example.prototype.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.example.prototype.ui.child.CapturePermissionActivity


class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_CAPTURE_STARTED = "com.example.prototype.CAPTURE_STARTED"
    }

    private lateinit var windowManager: WindowManager
    private var blockerView: ComposeView? = null // Updated to ComposeView

    // --- BROADCAST RECEIVER ---
    private val captureStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE_STARTED) {
                removeBlocker()
            }
        }
    }

    // --- LIFECYCLE ---
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 1. Register receiver to know when to dismiss (keep this as is)
        val filter = IntentFilter(ACTION_CAPTURE_STARTED)
        registerReceiver(captureStartReceiver, filter, RECEIVER_NOT_EXPORTED)

        // 2. LOGIC CHECK: Only show the blocker if capture is NOT already running
        if (!ScreenCaptureService.CaptureState.isRunning) {
            showBlocker()
        } else {
            // If it's already running, this service is not needed right now
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(captureStartReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver not registered")
        }
        removeBlocker()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- OVERLAY LOGIC ---

    private fun showBlocker() {
        if (blockerView != null) return

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            // Initialize ComposeView
            blockerView = ComposeView(this).apply {
                // Attach a fake lifecycle owner so Compose can run in a Service
                val lifecycleOwner = ServiceLifecycleOwner()
                lifecycleOwner.attachToView(this)

                setContent {
                    MaterialTheme {
                        MonitoringBlocker(onEnableClick = { startPermissionActivity() }, onDeclineClick = { handleDecline() })
                    }
                }
            }

            windowManager.addView(blockerView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
        }
    }

    private fun removeBlocker() {
        blockerView?.let {
            windowManager.removeView(it)
            blockerView = null
            stopSelf()
        }
    }

    private fun startPermissionActivity() {
        val intent = Intent(this, CapturePermissionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        startActivity(intent)
    }

    /**
     * Logic: Minimizes the current app and removes the overlay.
     */
    private fun handleDecline() {
        // 1. Move the current task (Facebook) to the background by going Home
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // 2. Remove the blocker so the device is usable
        removeBlocker()
    }
}

// --- COMPOSABLE UI ---

@Composable
fun MonitoringBlocker(onEnableClick: () -> Unit, onDeclineClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Monitoring Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black // In Dark Mode, ensures visibility
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "To use Facebook, you must enable screen monitoring.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Primary Action: Enable
                Button(
                    onClick = onEnableClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Enable & Continue", color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Choice Action: Exit App
                TextButton(
                    onClick = onDeclineClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now (Exit App)", color = Color.Gray)
                }
            }
        }
    }
}
// --- PREVIEWS ---

/**
 * Preview for the Monitoring Block Overlay.
 * Simulates how the screen looks when the child is blocked from using the app.
 */
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MonitoringBlockerPreview() {
    MaterialTheme {
        // We use a Box to simulate the full-screen overlay effect
        Box(modifier = Modifier.fillMaxSize()) {
            MonitoringBlocker(
                onEnableClick = {}, onDeclineClick = {}
            )
        }
    }
}