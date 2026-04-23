package com.example.oversee.ui.child

// --- ANDROID & CORE ---
import android.app.Activity
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

// --- JETPACK COMPOSE UI ---
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// --- PROJECT SPECIFIC ---
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.service.ScreenCaptureService
import com.example.oversee.ui.components.inputs.OverSeePinPad
import com.example.oversee.ui.components.inputs.OverSeeTextField
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.theme.AppTheme
import com.google.firebase.FirebaseApp

// --- UTILS ---
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

// --- SECURE ADMIN COLOR THEME ---
val AdminBackground = Color(0xFFF1F5F9)
val AdminPrimary = Color(0xFF0F766E)
val AdminPrimaryLight = Color(0xFFCCFBF1)
val AdminTextGray = Color(0xFF64748B)
val StatusGreen = Color(0xFF10B981)
val StatusRed = Color(0xFFEF4444)

// Standard Box Padding
val BoxPadding = 24.dp

@Composable
fun ChildDashboardRoute(onLogoutClick: () -> Unit, onDebugResetRole: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    var deviceId by remember { mutableStateOf("Loading...") }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val healthStates = remember { mutableStateMapOf<String, Boolean>() }

    var parentName by remember { mutableStateOf(AppPreferenceManager.getString(context, "parent_name", "Unknown Parent")) }
    var parentId by remember { mutableStateOf(AppPreferenceManager.getString(context, "parent_id", "---")) }
    var lastSyncedTime by remember { mutableStateOf(AppPreferenceManager.getString(context, "last_synced", "Never")) }

    var isReady by remember { mutableStateOf(false) }
    var isDeviceLinked by remember { mutableStateOf(UserRepository.getLocalTargetId(context) != "NOT_LINKED") }
    var showSettingsDialog by remember { mutableStateOf(false) }
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

        // This only acts as the "Gatekeeper" to skip the setup wizard.
        isReady = (healthStates["Accessibility"] == true && healthStates["Overlay"] == true) || debugSkipPermissions
    }

    LaunchedEffect(Unit) {
        val uid = AuthRepository.getUserId()
        if (uid != null) UserRepository.initializeDeviceId(context, uid) { id -> deviceId = id }
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
        context.registerReceiver(receiver, IntentFilter("com.example.oversee.CONSOLE_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
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
                TextButton(onClick = onExitApp) { Text("Exit to Home Screen", color = AdminTextGray, fontWeight = FontWeight.Bold) }
            }
        )
    } else {
        ChildDashboardScreen(
            deviceId = deviceId, parentName = parentName, parentId = parentId, checks = healthStates,
            onFixPermission = { SystemHealthManager.navigateToSetting(context, it) },
            onSettingsClick = { showSettingsDialog = true }
        )

        if (showSettingsDialog) {
            ChildSettingsDialog(
                deviceId = deviceId, lastSyncedTime = lastSyncedTime, consoleLogs = consoleLogs, isRefreshing = isRefreshing,
                onDismiss = { showSettingsDialog = false },
                onForceSync = {
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
                },
                onChangePin = {
                    AppPreferenceManager.saveString(context, "child_pin", "")
                    savedChildPin = ""
                    showSettingsDialog = false
                },
                onEditId = { newId ->
                    val uid = AuthRepository.getUserId()
                    if (uid != null) {
                        UserRepository.updateDeviceId(context, uid, newId) { success ->
                            if (success) deviceId = newId
                        }
                    }
                },
                onExitApp = onExitApp,
                onDebugResetRole = onDebugResetRole
            )
        }
    }
}

// =========================================================================
// SMART PIN FLOW LOGIC
// =========================================================================

@Composable
fun SmartPinSetupFlow(parentPin: String, onPinSaved: (String, Boolean) -> Unit) {
    var stage by remember { mutableStateOf(if (parentPin.isNotBlank()) "ASK_PARENT" else "CREATE_NEW") }
    var tempPin by remember { mutableStateOf("") }
    var errorTxt by remember { mutableStateOf<String?>(null) }
    var syncParentPin by remember { mutableStateOf(false) }

    when (stage) {
        "ASK_PARENT" -> {
            OverSeePinPad(
                title = "Use Parent PIN?",
                subtitle = "An existing Parent Dashboard PIN was found. Enter it to secure this device as well.",
                errorText = errorTxt,
                onPinComplete = { entered ->
                    if (entered == parentPin) onPinSaved(entered, false) else errorTxt = "Incorrect Parent PIN."
                },
                bottomContent = {
                    TextButton(onClick = { stage = "CREATE_NEW" }) { Text("Create a Different PIN Instead", color = AdminPrimary, fontWeight = FontWeight.Bold) }
                }
            )
        }
        "CREATE_NEW" -> {
            OverSeePinPad(
                title = "Create PIN",
                subtitle = "Set a 4-digit PIN to secure this dashboard from being tampered with.",
                onPinComplete = { entered ->
                    tempPin = entered
                    stage = "CONFIRM_NEW"
                },
                bottomContent = {
                    if (parentPin.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { syncParentPin = !syncParentPin }) {
                            Checkbox(checked = syncParentPin, onCheckedChange = { syncParentPin = it }, colors = CheckboxDefaults.colors(checkedColor = AdminPrimary))
                            Text("Use this PIN for Parent Dashboard too", fontSize = 14.sp, color = Color.DarkGray)
                        }
                    }
                }
            )
        }
        "CONFIRM_NEW" -> {
            OverSeePinPad(
                title = "Confirm PIN",
                subtitle = "Re-enter your 4-digit PIN to confirm.",
                errorText = errorTxt,
                onPinComplete = { entered ->
                    if (entered == tempPin) onPinSaved(entered, syncParentPin)
                    else {
                        errorTxt = "PINs do not match. Try again."
                        stage = "CREATE_NEW"
                    }
                },
                bottomContent = {
                    TextButton(onClick = { stage = "CREATE_NEW"; errorTxt = null }) { Text("Start Over", color = AdminTextGray, fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}

// =========================================================================
// MAIN DASHBOARD UI (Ultra Minimalist Redesign)
// =========================================================================

@Composable
fun ChildDashboardScreen(
    deviceId: String, parentName: String, parentId: String, checks: Map<String, Boolean>,
    onFixPermission: (String) -> Unit, onSettingsClick: () -> Unit
) {
    val isSystemActive = checks["Accessibility"] == true &&
            checks["Notifications"] == true &&
            checks["Overlay"] == true &&
            checks["Internet"] == true &&
            checks["Firebase"] == true

    val topHalfColor = if (isSystemActive) StatusGreen else StatusRed

    Box(modifier = Modifier.fillMaxSize().background(topHalfColor)) {

        // --- TOP HALF: MASSIVE VISUAL STATUS & INFO ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // Let it fill 70% of the screen
                .padding(BoxPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- INFO HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Device ID: $deviceId", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Connected to: $parentName", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                }
                Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text("Parent ID: $parentId", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // --- MASSIVE CENTERED CIRCLE ---
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(Color.White, CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSystemActive) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = "System Ready", tint = StatusGreen, modifier = Modifier.size(100.dp))
                } else {
                    Icon(Icons.Rounded.Shield, contentDescription = "Action Required", tint = StatusRed, modifier = Modifier.size(100.dp))
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

            Spacer(Modifier.weight(1f))
        }

        // --- BOTTOM HALF: COMPACT SLIDING SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight() // Automatically shrinks to tightly wrap the grid
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = AdminBackground,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BoxPadding)
            ) {
                // Settings Icon (No Dashboard Text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { onSettingsClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = AdminPrimary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // The new ultra-compact single-row icon indicator!
                SystemHealthRowSection(checks, onFixPermission)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =========================================================================
// NEW: ULTRA COMPACT SERVICE STATUS INDICATOR ROW
// =========================================================================

@Composable
fun SystemHealthRowSection(checks: Map<String, Boolean>, onFix: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Service Status",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AdminTextGray,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Single row spreading the icons perfectly across the width
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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

                // Raw Icons: No text, no background. Just the color state.
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isOk) StatusGreen else StatusRed,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { if (!isOk) onFix(label) }
                )
            }
        }
    }
}

// =========================================================================
// SETTINGS DIALOG (Holds all the extra features)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSettingsDialog(
    deviceId: String, lastSyncedTime: String, consoleLogs: List<String>, isRefreshing: Boolean,
    onDismiss: () -> Unit, onForceSync: () -> Unit, onChangePin: () -> Unit, onEditId: (String) -> Unit,
    onExitApp: () -> Unit, onDebugResetRole: () -> Unit
) {
    var showSyncHistory by remember { mutableStateOf(false) }
    var showActivityConsole by remember { mutableStateOf(false) }
    var showEditId by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Device Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = AdminBackground
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(BoxPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                item { Text("Synchronization", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AdminPrimary, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.Sync, title = "Sync Now", subtitle = "Last synced: $lastSyncedTime", onClick = onForceSync, isLoading = isRefreshing)
                            Divider(color = AdminBackground)
                            SettingsRow(icon = Icons.Rounded.History, title = "Sync History", onClick = { showSyncHistory = true })
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Security", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AdminPrimary, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.LockReset, title = "Change PIN", onClick = onChangePin)
                            Divider(color = AdminBackground)
                            SettingsRow(icon = Icons.Rounded.ExitToApp, title = "Lock & Exit Dashboard", onClick = onExitApp, isDestructive = true)
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Debug & Advanced", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AdminTextGray, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.Edit, title = "Edit Local Device ID", onClick = { showEditId = true })
                            Divider(color = AdminBackground)
                            SettingsRow(icon = Icons.Rounded.Terminal, title = "View Activity Console", onClick = { showActivityConsole = true })
                            Divider(color = AdminBackground)
                            SettingsRow(icon = Icons.Rounded.Refresh, title = "Reset Role (Keep ID)", onClick = onDebugResetRole, isDestructive = true)
                        }
                    }
                }
            }
        }
    }

    if (showSyncHistory) SyncHistoryDialog(consoleLogs, onDismiss = { showSyncHistory = false })
    if (showActivityConsole) ActivityConsoleDialog(consoleLogs, onDismiss = { showActivityConsole = false })
    if (showEditId) EditDeviceIdDialog(deviceId, onDismiss = { showEditId = false }, onConfirm = { onEditId(it); showEditId = false })
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit, isDestructive: Boolean = false, isLoading: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDestructive) StatusRed else AdminTextGray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDestructive) StatusRed else Color.Black)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = AdminTextGray)
        }
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AdminPrimary)
        else Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
    }
}

// =========================================================================
// SUB-COMPONENTS & DIALOGS
// =========================================================================

@Composable
fun SyncHistoryDialog(consoleLogs: List<String>, onDismiss: () -> Unit) {
    val syncEvents = remember(consoleLogs) { consoleLogs.filter { it.contains("Sync Event") } }
    OverSeeDialog(title = "Sync History", description = "Recent manual and automated sync events.", confirmText = "Close", onConfirm = onDismiss, onDismiss = onDismiss) {
        if (syncEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("No recent syncs recorded.", color = AdminTextGray, fontSize = 14.sp) }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(syncEvents) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = AdminBackground), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudSync, null, tint = AdminPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(log, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityConsoleDialog(consoleLogs: List<String>, onDismiss: () -> Unit) {
    OverSeeDialog(title = "Activity Console", description = "System logs for debugging.", confirmText = "Close", onConfirm = onDismiss, onDismiss = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AdminBackground)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(consoleLogs) { log ->
                    Text(text = log, color = Color.DarkGray, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
fun EditDeviceIdDialog(currentId: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentId) }
    OverSeeDialog(title = "Edit Device ID", confirmText = "Save", onConfirm = { onConfirm(text) }, onDismiss = onDismiss) {
        OverSeeTextField(value = text, onValueChange = { text = it }, label = "Device ID", modifier = Modifier.fillMaxWidth())
    }
}

// Setup Screens omitted for brevity (they remain identical to your previous code)
@Composable
fun ChildLinkSetupScreen(deviceId: String, onLinkConfirmed: () -> Unit, onLogout: () -> Unit) { /* Existing UI */ }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsSetupScreen(checks: Map<String, Boolean>, onFixPermission: (String) -> Unit, onCheckAgain: () -> Unit, onDebugSkip: () -> Unit) {
    val allGood = checks["Accessibility"] == true && checks["Overlay"] == true
    Scaffold(
        containerColor = AdminBackground,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(BoxPadding), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, null, tint = AdminPrimary) }
                Spacer(Modifier.width(20.dp))
                Text(text = "Required Permissions", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = BoxPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "OverSee needs these permissions to monitor the device in the background.", fontSize = 15.sp, textAlign = TextAlign.Center, color = AdminTextGray, lineHeight = 22.sp)
            Spacer(Modifier.height(36.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
                PermissionGridItem(title = "Accessibility", icon = Icons.Rounded.Visibility, isGranted = checks["Accessibility"] == true, modifier = Modifier.weight(1f), onClick = { onFixPermission("Accessibility") })
                PermissionGridItem(title = "Overlay", icon = Icons.Rounded.Layers, isGranted = checks["Overlay"] == true, modifier = Modifier.weight(1f), onClick = { onFixPermission("Overlay") })
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onCheckAgain, enabled = allGood, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (allGood) AdminPrimary else Color.LightGray), shape = RoundedCornerShape(16.dp)) {
                Text(text = if (allGood) "START MONITORING" else "COMPLETE SETUP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDebugSkip) { Text("Debug: Skip Permissions", color = AdminTextGray, fontSize = 13.sp) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionGridItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isGranted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(190.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(64.dp).background(if (isGranted) StatusGreen.copy(alpha = 0.1f) else AdminPrimaryLight, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = if (isGranted) StatusGreen else AdminPrimary, modifier = Modifier.size(32.dp)) }
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isGranted) StatusGreen else AdminPrimary), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) {
                Text(text = if (isGranted) "Granted" else "Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            deviceId = "847291", parentName = "Jane Doe", parentId = "JANE-123",
            checks = mapOf("Accessibility" to true, "Notifications" to true, "Overlay" to true, "Internet" to true, "Firebase" to true, "Capture" to false),
            onFixPermission = {}, onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6", name = "2. Child Dashboard (Action Required)")
@Composable
fun ChildDashboardScreenPreviewError() {
    MaterialTheme {
        ChildDashboardScreen(
            deviceId = "847291", parentName = "Jane Doe", parentId = "JANE-123",
            checks = mapOf("Accessibility" to true, "Notifications" to false, "Overlay" to true, "Internet" to true, "Firebase" to true, "Capture" to false),
            onFixPermission = {}, onSettingsClick = {}
        )
    }
}

// --- UTILS ---
private object SystemHealthManager {
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
            "Overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            else -> null
        }
        intent?.let { context.startActivity(it) }
    }
}