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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.PunishmentRepository
import com.example.oversee.data.local.AppPreferenceManager
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
        val newMode = intent?.getStringExtra(EXTRA_OVERLAY_MODE) ?: MODE_REQUIRE_MONITORING

        // Never downgrade or tear down an active penalty/punishment blocker. Once a severe
        // warning is showing it must stay until the timer ends AND the parent approves —
        // a stray REQUIRE_MONITORING (or a duplicate start) must not dismiss it.
        if (currentMode == MODE_SEVERE_WARNING && blockerView != null && newMode != MODE_SEVERE_WARNING) {
            return START_STICKY
        }

        currentMode = newMode

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
    if (!isWarning) {
        RequireMonitoringContent(onPrimaryClick = onPrimaryClick, onSecondaryClick = onSecondaryClick)
        return
    }
    SevereWarningContent(onPrimaryClick = onPrimaryClick, onTimerComplete = onTimerComplete)
}

@Composable
private fun RequireMonitoringContent(onPrimaryClick: () -> Unit, onSecondaryClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Monitoring Required", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(16.dp))
                Text(
                    "To use Facebook, you must enable screen monitoring.",
                    fontSize = 16.sp, color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Enable & Continue", color = Color.White) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSecondaryClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Not Now (Exit App)", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SevereWarningContent(onPrimaryClick: () -> Unit, onTimerComplete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val timeoutEnabled = AppPreferenceManager.getBoolean(context, "timeout_enabled", false)
    val punishmentEnabled = AppPreferenceManager.getBoolean(context, "punishment_enabled", false)
    val unlockTime = AppPreferenceManager.getLong(context, "app_unlock_time", 0L)
    val chores = remember { parseChores(AppPreferenceManager.getString(context, "punishment_chores", "[]")) }
    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(chores.size) { add(false) } } }

    var timeLeftSecs by remember { mutableLongStateOf(maxOf(0L, (unlockTime - System.currentTimeMillis()) / 1000)) }
    LaunchedEffect(Unit) {
        while (timeLeftSecs > 0) {
            kotlinx.coroutines.delay(1000)
            timeLeftSecs = maxOf(0L, (unlockTime - System.currentTimeMillis()) / 1000)
        }
    }

    // Watch parent approval in real time.
    var fid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { DeviceRepository.getFid { fid = it } }
    var status by remember {
        mutableStateOf(AppPreferenceManager.getString(context, "punishment_status", PunishmentRepository.STATUS_NONE))
    }
    DisposableEffect(fid) {
        val f = fid
        val reg = if (f != null) {
            PunishmentRepository.listen(f) { snap ->
                status = snap.getString(PunishmentRepository.FIELD_PUNISHMENT_STATUS)
                    ?: PunishmentRepository.STATUS_NONE
            }
        } else null
        onDispose { reg?.remove() }
    }

    val timerDone = !timeoutEnabled || timeLeftSecs <= 0L
    val choresDone = !punishmentEnabled || chores.isEmpty() || checked.all { it }
    val cleared = !punishmentEnabled || status == PunishmentRepository.STATUS_CLEARED
    val canUnlock = timerDone && cleared

    // Auto-dismiss once everything is satisfied (e.g. parent approves after the timer ran out).
    LaunchedEffect(canUnlock) {
        if (canUnlock && punishmentEnabled) {
            fid?.let { PunishmentRepository.resetStatus(it) }
            onTimerComplete()
        }
    }

    val title = when {
        !timerDone -> "Timeout Active"
        punishmentEnabled && status != PunishmentRepository.STATUS_CLEARED -> "Complete Your Chores"
        else -> "Break Complete"
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).heightIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(16.dp))

                if (!timerDone) {
                    val mins = timeLeftSecs / 60
                    val secs = timeLeftSecs % 60
                    Text("We noticed some highly negative interactions.", fontSize = 14.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(String.format(java.util.Locale.US, "%02d:%02d", mins, secs), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFFD32F2F))
                } else if (!(punishmentEnabled && status != PunishmentRepository.STATUS_CLEARED)) {
                    Text(
                        "You may now return to the app. Please be mindful of your interactions.",
                        fontSize = 16.sp, color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Chore checklist
                if (punishmentEnabled && chores.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Finish these to be unlocked:", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val interactable = status == PunishmentRepository.STATUS_NONE || status == PunishmentRepository.STATUS_ACTIVE
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        chores.forEachIndexed { index, chore ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked.getOrElse(index) { false },
                                    onCheckedChange = { if (interactable) checked[index] = it },
                                    enabled = interactable
                                )
                                Text(chore, fontSize = 15.sp, color = Color.Black, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // --- Primary button: state machine ---
                val dismiss: () -> Unit = {
                    fid?.let { PunishmentRepository.resetStatus(it) }
                    onTimerComplete()
                }
                val requestUnlock: () -> Unit = {
                    fid?.let { PunishmentRepository.requestUnlock(it) }
                    status = PunishmentRepository.STATUS_PENDING_APPROVAL
                }
                val noop: () -> Unit = {}
                val (btnLabel, btnEnabled, btnAction) = when {
                    canUnlock -> Triple("Return to Facebook", true, dismiss)
                    punishmentEnabled && status == PunishmentRepository.STATUS_CLEARED -> Triple("Approved — waiting for timeout", false, noop)
                    punishmentEnabled && status == PunishmentRepository.STATUS_PENDING_APPROVAL -> Triple("Waiting for parent approval…", false, noop)
                    punishmentEnabled && choresDone -> Triple("Request Unlock", true, requestUnlock)
                    punishmentEnabled && !choresDone -> Triple("Complete all chores", false, noop)
                    else -> Triple("Exit App", true, onPrimaryClick)
                }

                Button(
                    onClick = btnAction,
                    enabled = btnEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) { Text(btnLabel, color = Color.White) }
            }
        }
    }
}

private fun parseChores(json: String): List<String> {
    return try {
        val arr = org.json.JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}