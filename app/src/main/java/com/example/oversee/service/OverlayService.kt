package com.example.oversee.service

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.child.CapturePermissionActivity

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_CAPTURE_STARTED = "com.example.oversee.CAPTURE_STARTED"

        const val EXTRA_OVERLAY_MODE = "overlay_mode"
        const val MODE_REQUIRE_MONITORING = "mode_require_monitoring"
        const val MODE_SEVERE_WARNING = "mode_severe_warning"
    }

    private lateinit var windowManager: WindowManager
    private var blockerView: ComposeView? = null
    private var currentMode: String = MODE_REQUIRE_MONITORING

    private val captureStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE_STARTED) {
                removeBlocker()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(ACTION_CAPTURE_STARTED)
        registerReceiver(captureStartReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentMode = intent?.getStringExtra(EXTRA_OVERLAY_MODE) ?: MODE_REQUIRE_MONITORING

        if (currentMode == MODE_REQUIRE_MONITORING && ScreenCaptureService.CaptureState.isRunning) {
            stopSelf()
            return START_STICKY
        }

        showBlocker()
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(captureStartReceiver) } catch (e: Exception) {}
        removeBlocker()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

            blockerView = ComposeView(this).apply {
                val lifecycleOwner = ServiceLifecycleOwner()
                lifecycleOwner.attachToView(this)

                setContent {
                    MaterialTheme {
                        OverseeOverlay(
                            mode = currentMode,
                            onPrimaryClick = {
                                if (currentMode == MODE_REQUIRE_MONITORING) startPermissionActivity()
                                else handleDecline()
                            },
                            onSecondaryClick = { handleDecline() },
                            onTimerComplete = { removeBlocker() } // Dissolves the overlay seamlessly!
                        )
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
            // Don't stopSelf() here if you want it to be re-triggerable quickly
        }
    }

    private fun startPermissionActivity() {
        val intent = Intent(this, CapturePermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun handleDecline() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        removeBlocker()
    }
}

@Composable
fun OverseeOverlay(
    mode: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onTimerComplete: () -> Unit = {} // Added default empty lambda for backward compatibility
) {
    val isWarning = mode == OverlayService.MODE_SEVERE_WARNING
    val context = androidx.compose.ui.platform.LocalContext.current

    val unlockTime = com.example.oversee.data.local.AppPreferenceManager.getLong(context, "app_unlock_time", 0L)
    var timeLeftSecs by remember { mutableLongStateOf(maxOf(0L, (unlockTime - System.currentTimeMillis()) / 1000)) }

    LaunchedEffect(isWarning) {
        if (isWarning) {
            while (timeLeftSecs > 0) {
                kotlinx.coroutines.delay(1000)
                timeLeftSecs = maxOf(0L, (unlockTime - System.currentTimeMillis()) / 1000)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isWarning) Color(0xD9000000) else Color.Black.copy(alpha = 0.85f)),
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
                    text = if (isWarning && timeLeftSecs > 0) "Timeout Active" else if (isWarning) "Break Complete" else "Monitoring Required",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isWarning && timeLeftSecs > 0) {
                    val mins = timeLeftSecs / 60
                    val secs = timeLeftSecs % 60
                    Text("We noticed some highly negative interactions.", fontSize = 14.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(String.format(java.util.Locale.US, "%02d:%02d", mins, secs), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFFD32F2F))
                } else {
                    Text(
                        text = if (isWarning) "You may now return to the app. Please be mindful of your interactions." else "To use Facebook, you must enable screen monitoring.",
                        fontSize = 16.sp, color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        // FIX: Check if they are clicking the button after the timer is complete
                        if (isWarning && timeLeftSecs <= 0) onTimerComplete() else onPrimaryClick()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWarning) Color(0xFF1976D2) else Color(0xFFD32F2F)
                    )
                ) {
                    Text(if (isWarning && timeLeftSecs > 0) "Exit App" else if (isWarning) "Return to Facebook" else "Enable & Continue", color = Color.White)
                }

                if (!isWarning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSecondaryClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Not Now (Exit App)", color = Color.Gray)
                    }
                }
            }
        }
    }
}