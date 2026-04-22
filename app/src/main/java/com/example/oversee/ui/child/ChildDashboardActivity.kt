package com.example.oversee.ui.child

// --- ANDROID & CORE ---
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*

// --- PROJECT SPECIFIC ---
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.service.ScreenCaptureService
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.ui.theme.Responsive
import com.example.oversee.ui.welcome.RoleSelectionActivity
import com.example.oversee.ui.welcome.SignInActivity
import com.google.firebase.FirebaseApp

// --- UTILS ---
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class ChildDashboardActivity : ComponentActivity() {

    // --- STATE MANAGEMENT ---
    private var consoleLogs = mutableStateListOf<String>()

    // Health States
    private var healthStates = mutableStateMapOf<String, Boolean>()
    private var isReady = mutableStateOf(false)

    private val consoleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("message")?.let { addToConsole(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup Receivers & Theme
        registerReceiver(consoleReceiver, IntentFilter("com.example.oversee.CONSOLE_UPDATE"), RECEIVER_NOT_EXPORTED)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 2. Startup Initialization
        performHealthCheck()
        addToConsole("System Initialized.")

        setContent {
            MaterialTheme {
                if (!isReady.value) {
                    PermissionsSetupScreen(
                        checks = healthStates,
                        onFixPermission = { label -> navigateToSetting(label) },
                        onCheckAgain = { performHealthCheck() }
                    )
                } else {
                    ChildDashboardScreen(
                        consoleLogs = consoleLogs,
                        isReady = isReady.value,
                        checks = healthStates,
                        onFixPermission = { label -> navigateToSetting(label) },
                        onForceSync = { triggerSync() },
                        onLogout = { performLogout() },
                        onExit = { killApp() },
                        onDebugResetRole = { debugResetRole() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        performHealthCheck()
    }

    override fun onDestroy() {
        unregisterReceiver(consoleReceiver)
        super.onDestroy()
    }

    // --- LOGIC (Delegated to Repositories) ---

    private fun performHealthCheck() {
        healthStates["Accessibility"] = SystemHealthManager.isAccessibilityOn(this)
        healthStates["Notifications"] = SystemHealthManager.isNotificationOn(this)
        healthStates["Overlay"] = SystemHealthManager.isOverlayOn(this)
        healthStates["Internet"] = SystemHealthManager.isInternetOn(this)
        healthStates["Firebase"] = SystemHealthManager.isFirebaseReady(this)
        healthStates["Capture"] = SystemHealthManager.isScreenCaptureActive()

        isReady.value = healthStates["Accessibility"] == true &&
                healthStates["Overlay"] == true
    }

    private fun navigateToSetting(label: String) {
        val intent = when (label) {
            "Accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "Notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            "Overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            else -> null
        }
        intent?.let { startActivity(it) }
    }

    private fun triggerSync() {
        addToConsole("Sync Event: Syncing to cloud...")
        FirebaseSyncManager.syncPendingLogs(this) { uploaded, error ->
            runOnUiThread {
                when {
                    error != null -> addToConsole("Sync Event: Sync failed — $error")
                    uploaded == 0 -> addToConsole("Sync Event: Already up to date.")
                    else -> addToConsole("Sync Event: Sync complete — $uploaded incident(s) uploaded.")
                }
            }
        }
    }

    private fun addToConsole(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        consoleLogs.add(0, "[$timestamp] $message")
        if (consoleLogs.size > 50) consoleLogs.removeAt(50)
    }

    private fun debugResetRole() {
        UserRepository.clearLocalRole(this)
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }

    private fun performLogout() {
        UserRepository.clearLocalRole(this)
        AuthRepository.logout(this)
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun killApp() {
        finishAffinity()
        exitProcess(0)
    }
}

// --- COMPOSE UI COMPONENTS ---

@Composable
fun ChildDashboardScreen(
    consoleLogs: List<String>,
    isReady: Boolean,
    checks: Map<String, Boolean>,
    onFixPermission: (String) -> Unit,
    onForceSync: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
    onDebugResetRole: () -> Unit
)  {
    Scaffold(
        containerColor = AppTheme.Background,
        bottomBar = { StickyExitButton(onExit) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.PaddingDefault),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Child Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            ReadyBanner(visible = isReady)
            ConnectedDevicesCard()
            HealthCheckSection(checks, onFixPermission)
            ActivityConsole(consoleLogs)
            ActionRow(onForceSync, onLogout)
            Spacer(Modifier.height(24.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = onDebugResetRole,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Debug: Log Out of Role (Keep ID)", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ReadyBanner(visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = Color(0xFFE8F5E9),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, AppTheme.Success, RoundedCornerShape(12.dp))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = AppTheme.Success)
                Spacer(Modifier.width(12.dp))
                Text("Ready for monitoring", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun ConnectedDevicesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Connected to:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = AppTheme.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Parent Device (Active)", fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthCheckSection(checks: Map<String, Boolean>, onFix: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("System Health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            checks.forEach { (label, isOk) ->
                CompactHealthBadge(label, isOk, onClick = { if (!isOk) onFix(label) })
            }
        }
    }
}

@Composable
fun CompactHealthBadge(label: String, isOk: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) AppTheme.Success else AppTheme.Error,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF2E7D32) else Color(0xFFD32F2F))
        }
    }
}

@Composable
fun ActivityConsole(logs: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Activity Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppTheme.Border),
            colors = CardDefaults.cardColors(containerColor = AppTheme.Surface.copy(alpha = 0.9f))
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = if (log.contains("Event")) AppTheme.Primary else Color.DarkGray,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionRow(onForceSync: () -> Unit, onLogout: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onForceSync, Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
            Text("Sync Now")
        }
        OutlinedButton(onClick = onLogout, Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
            Text("Log Out")
        }
    }
}

@Composable
fun StickyExitButton(onExit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppTheme.Background.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Button(
            onClick = onExit,
            modifier = Modifier.padding(16.dp).fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ExitToApp, null)
            Spacer(Modifier.width(8.dp))
            Text("Deactivate & Exit App", fontWeight = FontWeight.Bold)
        }
    }
}

// PERMISSIONS SETUP SCREEN
@Composable
fun PermissionsSetupScreen(checks: Map<String, Boolean>, onFixPermission: (String) -> Unit, onCheckAgain: () -> Unit) {
    val allGood = checks["Accessibility"] == true && checks["Overlay"] == true

    Box(Modifier.fillMaxSize().background(AppTheme.Surface).padding(24.dp)) {
        Column(Modifier.fillMaxHeight().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Settings, null, Modifier.size(64.dp), tint = AppTheme.Primary)
            Text("Required Permissions", style = AppTheme.TitlePageStyle)
            Text("OverSee needs these permissions to detect dangerous content.", style = AppTheme.BodyBase, textAlign = TextAlign.Center, color = Color.Gray)

            Spacer(Modifier.height(16.dp))

            PermissionItem("Accessibility", "To detect dangerous content", checks["Accessibility"] == true) { onFixPermission("Accessibility") }
            PermissionItem("Display Overlay", "To show alert popups", checks["Overlay"] == true) { onFixPermission("Overlay") }

            Spacer(Modifier.weight(1f))

            Button(onClick = onCheckAgain, enabled = allGood, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if(allGood) AppTheme.Success else Color.Gray), shape = RoundedCornerShape(12.dp)) {
                Text(if (allGood) "START MONITORING" else "COMPLETE SETUP", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, desc: String, isGranted: Boolean, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isGranted) Color(0xFFE8F5E9) else AppTheme.Background), shape = RoundedCornerShape(12.dp), onClick = { if (!isGranted) onClick() }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(desc, fontSize = 12.sp, color = Color.Gray)
            }
            if (isGranted) Icon(Icons.Default.CheckCircle, null, tint = AppTheme.Success)
            else Button(onClick = onClick, Modifier.height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)) { Text("Enable", fontSize = 12.sp) }
        }
    }
}

private object SystemHealthManager {
    fun isAccessibilityOn(context: android.content.Context): Boolean {
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun isNotificationOn(context: android.content.Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isOverlayOn(context: android.content.Context): Boolean = Settings.canDrawOverlays(context)

    fun isInternetOn(context: android.content.Context): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    fun isFirebaseReady(context: android.content.Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun isScreenCaptureActive(): Boolean = ScreenCaptureService.CaptureState.isRunning
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun ChildDashboardPreview() {
    val mockChecks = mapOf(
        "Accessibility" to true,
        "Notifications" to true,
        "Overlay" to false,
        "Internet" to true,
        "Firebase" to true,
        "Capture" to false
    )
    val mockLogs = listOf(
        "[22:15:01] System Initialized.",
        "[22:15:05] App Event: Messenger OPENED",
        "[22:15:10] Sync Event: Local logs cleared.",
        "[22:15:12] Monitoring: Keylog intercepted."
    )

    ChildDashboardScreen(
        consoleLogs = mockLogs,
        isReady = false,
        checks = mockChecks,
        onFixPermission = {},
        onForceSync = {},
        onLogout = {},
        onExit = {},
        onDebugResetRole = {}
    )
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun PermissionsSetupPreview() {
    PermissionsSetupScreen(
        checks = mapOf("Accessibility" to true, "Overlay" to false),
        onFixPermission = {},
        onCheckAgain = {}
    )
}
