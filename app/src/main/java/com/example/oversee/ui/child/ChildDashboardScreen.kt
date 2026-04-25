package com.example.oversee.ui.child

// --- ANDROID & CORE ---
import android.app.Activity
import android.content.*
import android.widget.Toast
import androidx.core.content.ContextCompat

// --- JETPACK COMPOSE UI ---
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// --- PROJECT SPECIFIC ---
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.components.inputs.OverSeePinPad
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.utils.SystemHealthManager

// --- UTILS ---
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

// Standard Box Padding
val BoxPadding = 24.dp

@Composable
fun ChildDashboardRoute(onLogoutClick: () -> Unit, onDebugResetRole: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    var deviceId by remember { mutableStateOf("Loading...") }
    var displayUid by remember { mutableStateOf("------") }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val healthStates = remember { mutableStateMapOf<String, Boolean>() }

    var parentName by remember { mutableStateOf(UserRepository.getLocalName(context).ifBlank { "Unknown Parent" }) }
    var parentId by remember { mutableStateOf(AuthRepository.getUserId() ?: "---") }
    var lastSyncedTime by remember { mutableStateOf(AppPreferenceManager.getString(context, "last_synced", "Never")) }

    var isReady by remember { mutableStateOf(false) }
    var isDeviceLinked by remember { mutableStateOf(UserRepository.getLocalTargetId(context) != "NOT_LINKED") }

    // Dialog States
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // --- PIN STATE ---
    var savedChildPin by remember { mutableStateOf(AppPreferenceManager.getString(context, "child_pin", "")) }
    val parentPin = remember { AppPreferenceManager.getString(context, "parent_pin", "") }
    var isUnlocked by remember { mutableStateOf(false) }
    var debugSkipPermissions by remember { mutableStateOf(false) }

    // Helpers
    val addToConsole = { message: String ->
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        consoleLogs.add(0, "[$timestamp] $message")
        if (consoleLogs.size > 50) consoleLogs.removeAt(50)
    }

    val performHealthCheck = {
        healthStates["Accessibility"] = SystemHealthManager.isAccessibilityOn(context)
        healthStates["Notifications"] = SystemHealthManager.isNotificationOn(context)
        healthStates["Overlay"] = SystemHealthManager.isOverlayOn(context)
        healthStates["Internet"] = SystemHealthManager.isInternetOn(context)
        healthStates["Firebase"] = SystemHealthManager.isFirebaseReady(context)
        healthStates["Capture"] = SystemHealthManager.isScreenCaptureActive()

        isReady = (healthStates["Accessibility"] == true && healthStates["Overlay"] == true) || debugSkipPermissions
    }

    // FIXED: Explicitly declared as () -> Unit to satisfy Compose Button requirements
    val triggerSync: () -> Unit = {
        isRefreshing = true
        FirebaseSyncManager.syncPendingLogs(context)
        addToConsole("Sync Event: Manual cloud synchronization triggered.")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isRefreshing = false
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            lastSyncedTime = "Today, $time"
            AppPreferenceManager.saveString(context, "last_synced", lastSyncedTime)
            addToConsole("Sync Event: Complete.")
        }, 1500)
    }

    LaunchedEffect(Unit) {
        val uid = AuthRepository.getUserId()
        if (uid != null) {
            UserRepository.initializeDeviceId(context, uid) { id ->
                deviceId = id
                if (displayUid == "------") displayUid = DeviceRepository.toDisplayCode(id)
            }
            DeviceRepository.getOrCreateDisplayUid(uid) { uid6 ->
                if (uid6.isNotBlank()) displayUid = uid6
            }
        }
        performHealthCheck()
        addToConsole("System Initialized.")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) performHealthCheck()
            if (event == Lifecycle.Event.ON_PAUSE) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("message")?.let { addToConsole(it) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("com.example.oversee.CONSOLE_UPDATE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val onExitApp = {
        val activity = context as? Activity
        activity?.finishAffinity()
        exitProcess(0)
    }

    // --- ROUTING ENGINE ---
    if (!isDeviceLinked) {
        ChildLinkSetupScreen(deviceId = deviceId, onLinkConfirmed = { isDeviceLinked = true }, onLogout = onLogoutClick)
    } else if (!isReady) {
        PermissionsSetupScreen(
            checks = healthStates,
            onFixPermission = { SystemHealthManager.navigateToSetting(context, it) },
            onCheckAgain = performHealthCheck,
            onDebugSkip = { debugSkipPermissions = true; performHealthCheck() }
        )
    } else if (savedChildPin.isBlank()) {
        SmartPinSetupFlow(
            parentPin = parentPin,
            onPinSaved = { newPin, saveForParentToo ->
                AppPreferenceManager.saveString(context, "child_pin", newPin)
                savedChildPin = newPin
                if (saveForParentToo) AppPreferenceManager.saveString(context, "parent_pin", newPin)
                isUnlocked = true
            }
        )
    } else if (!isUnlocked) {
        var errorTxt by remember { mutableStateOf<String?>(null) }
        OverSeePinPad(
            title = "Enter PIN",
            subtitle = "This device is monitored. Enter PIN to access the Child Device Dashboard.",
            errorText = errorTxt,
            onPinComplete = { entered ->
                if (entered == savedChildPin) {
                    errorTxt = null
                    isUnlocked = true
                } else {
                    errorTxt = "Incorrect PIN. Try again."
                }
            },
            bottomContent = {
                TextButton(onClick = onExitApp) { Text("Exit to Home Screen", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
            }
        )
    } else {
        ChildDashboardScreen(
            deviceId = deviceId, parentName = parentName, checks = healthStates,
            isRefreshing = isRefreshing, lastSyncedTime = lastSyncedTime, onForceSync = triggerSync,
            onFixPermission = { SystemHealthManager.navigateToSetting(context, it) },
            onSettingsClick = { showSettingsDialog = true },
            onInfoClick = { showInfoDialog = true }
        )

        // --- ACTIVE DIALOGS ---
        if (showInfoDialog) {
            HowToUseDialog(onDismiss = { showInfoDialog = false })
        }

        if (showSettingsDialog) {
            ChildSettingsDialog(
                deviceId = deviceId, accountId = displayUid, parentId = parentId, parentName = parentName, lastSyncedTime = lastSyncedTime,
                consoleLogs = consoleLogs,
                onDismiss = { showSettingsDialog = false },
                onChangePin = {
                    AppPreferenceManager.saveString(context, "child_pin", "")
                    savedChildPin = ""
                    showSettingsDialog = false
                },
                onExitApp = onExitApp,
                onDebugResetRole = onDebugResetRole
            )
        }
    }
}

// =========================================================================
// MAIN DASHBOARD UI
// =========================================================================

@Composable
fun ChildDashboardScreen(
    deviceId: String, parentName: String, checks: Map<String, Boolean>,
    isRefreshing: Boolean, lastSyncedTime: String, onForceSync: () -> Unit,
    onFixPermission: (String) -> Unit, onSettingsClick: () -> Unit, onInfoClick: () -> Unit
) {
    val isSystemActive = checks["Accessibility"] == true &&
            checks["Notifications"] == true &&
            checks["Overlay"] == true &&
            checks["Internet"] == true &&
            checks["Firebase"] == true

    val topHalfColor = if (isSystemActive) AppTheme.ChildSuccess else AppTheme.ChildError

    Box(modifier = Modifier.fillMaxSize().background(topHalfColor)) {

        // --- TOP HALF: MASSIVE VISUAL STATUS & INFO ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // Let it fill 70% of the screen
                .padding(BoxPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- UPDATED: CLEAN HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Protected by OverSee", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            val dynamicCircleSize = com.example.oversee.ui.theme.Responsive.dashboardCircleSize()
            Box(
                modifier = Modifier
                    .size(dynamicCircleSize)
                    .background(Color.White, CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ){
                if (isSystemActive) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = "System Ready", tint = AppTheme.ChildSuccess, modifier = Modifier.size(100.dp))
                } else {
                    Icon(Icons.Rounded.Shield, contentDescription = "Action Required", tint = AppTheme.ChildError, modifier = Modifier.size(100.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (isSystemActive) "System Active" else "Action Required",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSystemActive) "All background services are running securely." else "Important permissions are missing or disabled.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            // --- IN-DASHBOARD SYNC BUTTON WITH LAST SYNC TEXT ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onForceSync,
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // NEW: Shows the exact time beneath the button
                Text(
                    text = "Last synced: $lastSyncedTime",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.weight(1f))
        }

        // --- BOTTOM HALF: COMPACT SLIDING SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = AppTheme.ChildBackground,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BoxPadding)
            ) {
                // Toolbar (Info & Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { onInfoClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "How to use", tint = AppTheme.ChildAccent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { onSettingsClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = AppTheme.ChildAccent, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                SystemHealthRowSection(checks, onFixPermission)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =========================================================================
// INTERACTIVE SERVICE STATUS INDICATOR
// =========================================================================

@Composable
fun SystemHealthRowSection(checks: Map<String, Boolean>, onFix: (String) -> Unit) {
    val hasError = checks.values.any { !it }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "Service Status",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.ChildTextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )

        if (hasError) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = AppTheme.ChildError, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Tap red icons to grant permissions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.ChildError
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val checkKeys = listOf("Accessibility", "Notifications", "Overlay", "Internet", "Firebase", "Capture")

            checkKeys.forEach { label ->
                val isOk = checks[label] == true

                val icon = when(label) {
                    "Accessibility" -> Icons.Rounded.Visibility
                    "Notifications" -> Icons.Rounded.Notifications
                    "Overlay" -> Icons.Rounded.Layers
                    "Internet" -> Icons.Rounded.Wifi
                    "Firebase" -> Icons.Rounded.CloudSync
                    "Capture" -> Icons.Rounded.Videocam
                    else -> Icons.Rounded.Settings
                }

                val shortLabel = when(label) {
                    "Accessibility" -> "Access"
                    "Notifications" -> "Alerts"
                    "Overlay" -> "Overlay"
                    "Internet" -> "Network"
                    "Firebase" -> "Sync"
                    "Capture" -> "Screen"
                    else -> "Setting"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isOk) AppTheme.ChildSuccess.copy(alpha = 0.1f) else AppTheme.ChildError.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!isOk) {
                                    onFix(label)
                                } else {
                                    Toast.makeText(context, "$label is already active", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isOk) AppTheme.ChildSuccess else AppTheme.ChildError,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = shortLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.ChildTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6", name = "1. Child Dashboard (Active)")
@Composable
fun ChildDashboardScreenPreviewActive() {
    MaterialTheme {
        ChildDashboardScreen(
            deviceId = "847291", parentName = "Jane Doe",
            checks = mapOf("Accessibility" to true, "Notifications" to true, "Overlay" to true, "Internet" to true, "Firebase" to true, "Capture" to false),
            isRefreshing = false, lastSyncedTime = "Today, 10:45 AM", onForceSync = {},
            onFixPermission = {}, onSettingsClick = {}, onInfoClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6", name = "2. Child Dashboard (Action Required)")
@Composable
fun ChildDashboardScreenPreviewError() {
    MaterialTheme {
        ChildDashboardScreen(
            deviceId = "847291", parentName = "Jane Doe",
            checks = mapOf("Accessibility" to true, "Notifications" to false, "Overlay" to true, "Internet" to true, "Firebase" to true, "Capture" to false),
            isRefreshing = true, lastSyncedTime = "Yesterday, 3:12 PM", onForceSync = {},
            onFixPermission = {}, onSettingsClick = {}, onInfoClick = {}
        )
    }
}

@Preview(showBackground = true, name = "1. Transfer Dialog Pop-up", widthDp = 360, heightDp = 640)
@Composable
fun PreviewTransferDialog() {
    MaterialTheme {
        // We render an empty box as the background to simulate the screen
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
            com.example.oversee.ui.components.dialogs.OverSeeDialog(
                title = "Device Already Linked",
                description = "This account already has an existing child device linked. Do you want to transfer data and link this device instead?\n\n(Old logs will be securely deleted).",
                confirmText = "Transfer & Link",
                dismissText = "Cancel",
                isDestructive = false, // Set to true if you want the button to be red
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}
