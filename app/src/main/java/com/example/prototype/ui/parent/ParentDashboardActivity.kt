package com.example.prototype.ui.parent

// --- ANDROID & CORE ---
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate

// --- JETPACK COMPOSE UI ---
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions

// --- COMPOSE MATERIAL & ICONS ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

// --- COMPOSE RUNTIME & TOOLS ---
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*

// --- FIREBASE & DATA ---
import com.example.prototype.data.remote.FirebaseSyncManager
import com.google.firebase.firestore.*

// --- PROJECT SPECIFIC ---
import com.example.prototype.ui.theme.AppTheme
import com.example.prototype.ui.welcome.RoleSelectionActivity // Or LoginActivity if you created it

// --- UTILS ---
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import com.example.prototype.data.AuthRepository
import com.example.prototype.data.IncidentRepository
import com.example.prototype.data.UserRepository
import com.example.prototype.ui.welcome.SignInActivity


class ParentDashboardActivity : ComponentActivity() {

    // --- STATE MANAGEMENT ---
    private val currentTargetId = mutableStateOf("NOT_LINKED")
    private val incidentsList = mutableStateOf<List<FirebaseSyncManager.LogEntry>>(emptyList())

    private var incidentList = mutableStateListOf<FirebaseSyncManager.LogEntry>()
     val isLoading = mutableStateOf(false)
    private var isRefreshing = mutableStateOf(false)


    // --- LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initial Load: Get ID from Local Repository
        loadLocalData()

        if (currentTargetId.value != "NOT_LINKED") refreshDashboard()

        val user = UserRepository.getLocalName(this)

        setContent {
            MaterialTheme {
                // UI States for Dialogs
                var showLinkDialog by remember { mutableStateOf(false) }
                var showLogoutDialog by remember { mutableStateOf(false) }

                val user = UserRepository.getLocalName(this)

                // If NOT_LINKED, show the setup screen first.
                if (currentTargetId.value == "NOT_LINKED") {
                    LinkDeviceSetupScreen(
                        user = user,
                        onLinkConfirmed = { newId -> updateTargetId(newId) },
                        onLogout = { performLogout() }
                    )
                } else {
                    // If already linked, show the actual dashboard
                    ParentDashboardScreen(
                        targetId = currentTargetId.value,
                        incidents = incidentList,
                        refreshing = isRefreshing.value,
                        onRefresh = { refreshDashboard() },
                        onHeaderSettingsClick = { showLinkDialog = true },
                        onBottomNavSettingsClick = { showLogoutDialog = true },
                        onDebugResetRole = { debugResetRole() }
                    )
                }

                // Dialog 1: Link Device
                if (showLinkDialog) {
                    LinkDeviceDialog(
                        onDismiss = { showLinkDialog = false },
                        onConfirm = { newId ->
                            updateTargetId(newId)
                            showLinkDialog = false
                        }
                    )
                }

                // Dialog 2: Logout / Settings Menu
                if (showLogoutDialog) {
                    LogoutDialog(
                        onDismiss = { showLogoutDialog = false },
                        onLogout = {
                            showLogoutDialog = false
                            performLogout()
                        }
                    )
                }
            }
        }
    }

    // --- DATA LOGIC ---

    private fun loadLocalData() {
        // 🟢 Repository Call: fast local read
        val savedId = UserRepository.getLocalTargetId(this)
        currentTargetId.value = savedId

        // If we have a valid ID, fetch the logs from cloud
        if (savedId != "NOT_LINKED") {
            fetchLogs()
        }
    }

    private fun updateTargetId(newId: String) {
        val uid = AuthRepository.getUserId() ?: return

        UserRepository.linkChildDevice(this, uid, newId) { success ->
            if (success) {
                currentTargetId.value = newId
                refreshDashboard()
                Toast.makeText(this, "Device Linked Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to link device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun linkChild(newChildId: String) {
        val uid = AuthRepository.getUserId() ?: return
        if (newChildId.length != 6) {
            Toast.makeText(this, "ID must be 6 digits", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading.value = true

        // 🟢 Repository Call: Orchestrates Cloud update + Local Save
        UserRepository.linkChildDevice(this, uid, newChildId) { success ->
            isLoading.value = false
            if (success) {
                currentTargetId.value = newChildId
                fetchLogs() // Immediately load logs for the new device
                Toast.makeText(this, "Device Linked!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to link. Check ID.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchLogs() {
        val target = currentTargetId.value
        if (target == "NOT_LINKED") return

        isLoading.value = true

        // 🟢 Repository Call: Fetches logs cleanly
        IncidentRepository.fetchRecentIncidents(
            childId = target,
            onSuccess = { logs ->
                isLoading.value = false
                incidentsList.value = logs
            },
            onError = { error ->
                isLoading.value = false
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

//    private fun loadSavedTargetId() {
//        val prefs = getSharedPreferences("AppConfig", MODE_PRIVATE)
//        currentTargetId.value = prefs.getString("target_id", "NOT_LINKED") ?: "NOT_LINKED"
//    }
//
//    private fun syncTargetIdWithCloud() {
//        val uid = AuthRepository.getUserId() ?: return
//
//        // 🟢 USE REPOSITORY
//        UserRepository.getUserProfile(
//            uid = uid,
//            onSuccess = { data ->
//                val cloudId = data["linked_child_id"] as? String ?: ""
//                if (cloudId.isNotEmpty() && cloudId != "NOT_LINKED") {
//                    updateLocalTargetId(cloudId) // Helper to save to prefs
//                    currentTargetId.value = cloudId
//                    refreshDashboard()
//                }
//            },
//            onError = { Log.e("Dashboard", it) }
//        )
//    }
//
//    // 🟢 UPDATED: Save to cloud whenever you link a new device
//    private fun updateTargetId(newId: String) {
//        val uid = AuthRepository.getUserId() ?: return
//
//        // 1. Update Local
//        updateLocalTargetId(newId)
//        currentTargetId.value = newId
//
//        // 🟢 USE REPOSITORY
//        UserRepository.linkChildDevice(
//            uid = uid,
//            childId = newId,
//            onSuccess = {
//                refreshDashboard()
//                Toast.makeText(this, "Link Synced", Toast.LENGTH_SHORT).show()
//            },
//            onError = { Toast.makeText(this, "Link Failed: $it", Toast.LENGTH_SHORT).show() }
//        )
//    }
//
    private fun refreshDashboard() {
        isRefreshing.value = true
        // 🟢 Repository Call: Fetch from cloud
        IncidentRepository.fetchRecentIncidents(
            childId = currentTargetId.value,
            onSuccess = { logs ->
                incidentList.clear()
                incidentList.addAll(logs)
                isRefreshing.value = false
            },
            onError = { error ->
                isRefreshing.value = false
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun performLogout() {
        // 🟢 Repository Call: Clears session and local data
        AuthRepository.logout(this)
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }
    /**
     * DEBUG: Clears only the 'role' key.
     * This forces the app back to Role Selection while keeping the Child Link intact.
     */
    private fun debugResetRole() {
        UserRepository.clearLocalRole(this) //
        startActivity(Intent(this, RoleSelectionActivity::class.java)) //
        finish()
    }
}

// --- COMPOSE UI SCREENS ---

@Composable
fun ParentDashboardScreen(
    targetId: String,
    incidents: List<FirebaseSyncManager.LogEntry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onHeaderSettingsClick: () -> Unit,
    onBottomNavSettingsClick: () -> Unit,
    onDebugResetRole: () -> Unit
) {
    Scaffold(
        bottomBar = { ParentBottomNavigation(onSettingsClick = onBottomNavSettingsClick) },
        containerColor = AppTheme.Background,
        contentColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.PaddingDefault),
            verticalArrangement = Arrangement.spacedBy(AppTheme.PaddingDefault)
        ) {
            // 1. Profile Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppTheme.CardCorner),
                colors = CardDefaults.cardColors(containerColor = AppTheme.Surface)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.LightGray)) {
                        Icon(Icons.Default.Person, null, Modifier.align(Alignment.Center))
                    }
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(targetId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Status: Active", color = AppTheme.Success, fontSize = 12.sp)
                    }
                    // Gear icon for Linking (Header)
                    IconButton(onClick = onHeaderSettingsClick) { Icon(Icons.Default.Link, "Link Device") }
                }
            }

            // 2. Statistics Card
            StatsCard(incidents)

            // 3. Log Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = AppTheme.Border, shape = RoundedCornerShape(12.dp))
                    .background(color = AppTheme.Surface, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("Recent Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                LogTable(incidents)

                Button(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (refreshing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("Refresh Data")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                TextButton(
                    onClick = onDebugResetRole,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Debug: Log Out of Role (Keep Link)", color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// --- REUSABLE COMPONENTS ---

@Composable
fun LogTable(incidents: List<FirebaseSyncManager.LogEntry>) {
    val grouped = incidents.groupBy {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(it.timestamp)).uppercase()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        grouped.forEach { (date, logs) ->
            Text(
                text = date,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.ExtraBold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppTheme.Border),
                colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Column {
                    TableHeader()
                    logs.forEach { incident ->
                        LogItem(incident)
                        if (incident != logs.last()) {
                            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FA))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.ColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Severity", Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Word", Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("App", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Time", Modifier.weight(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun LogItem(incident: FirebaseSyncManager.LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.ColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeverityBadge(severity = incident.severity, modifier = Modifier.weight(0.8f))
        Text(text = incident.word, modifier = Modifier.weight(1.2f), fontSize = 12.sp, maxLines = 1)
        Text(text = incident.app, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)

        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(incident.timestamp))
        Text(text = timeString, modifier = Modifier.weight(0.7f), fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
fun StatsCard(incidents: List<FirebaseSyncManager.LogEntry>) {
    val high = incidents.count { it.severity == "HIGH" }
    val med = incidents.count { it.severity == "MEDIUM" }
    val low = incidents.count { it.severity == "LOW" }
    val total = incidents.size.coerceAtLeast(1)
    val riskScore = (high + med).toFloat() / total

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shape = RoundedCornerShape(AppTheme.CardCorner),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { riskScore },
                    modifier = Modifier.size(90.dp),
                    strokeWidth = 8.dp,
                    color = if (riskScore > 0.5f) AppTheme.Error else AppTheme.Success,
                    trackColor = Color(0xFFE0E0E0)
                )
                Text("${(riskScore * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(modifier = Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBar("Low", low.toFloat() / total, AppTheme.Success)
                SeverityBar("Med", med.toFloat() / total, AppTheme.Warning)
                SeverityBar("High", high.toFloat() / total, AppTheme.Error)
            }
        }
    }
}

@Composable
fun SeverityBar(label: String, progress: Float, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = Color(0xFFF0F0F0)
        )
    }
}

@Composable
fun SeverityBadge(severity: String, modifier: Modifier = Modifier) {
    val (bg, text) = when (severity.uppercase()) {
        "HIGH" -> Color(0xFFFFEBEE) to AppTheme.Error
        "MEDIUM" -> Color(0xFFFFF3E0) to AppTheme.Warning
        else -> Color(0xFFE8F5E9) to AppTheme.Success
    }
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(AppTheme.BadgeCorner)) {
        Text(
            text = severity.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// --- DIALOGS & NAVIGATION ---

@Composable
fun LinkDeviceSetupScreen(
    user: String,
    onLinkConfirmed: (String) -> Unit,
    onLogout: () -> Unit
) {
    var childIdInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Surface)
            .padding(horizontal = 57.dp, vertical = 103.dp), // Matches Role Selection
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- TOP SECTION (Hello Text) ---
            Column(verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top)) {
                // Large Link Icon
                Icon(
                    modifier = Modifier.size(88.dp),
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = AppTheme.Primary
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Link Child Device",
                    style = AppTheme.TitlePageStyle,
                    textAlign = TextAlign.Left
                )
                Text(
                    text = "Link a child device to start monitoring.",
                    style = AppTheme.BodyBase,
                )
            }

            Spacer(modifier = Modifier.height(90.dp))

            // --- CENTER SECTION (Icon & Input) ---
            Column(
                modifier = Modifier.width(250.dp), // Slightly wider to fit text input
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.Top)
            ) {
                // Input Field
                OutlinedTextField(
                    value = childIdInput,
                    onValueChange = { input ->
                        // 🟢 1. Only allow digits AND 2. Limit length to 6
                        if (input.all { it.isDigit() } && input.length <= 6) {
                            childIdInput = input
                        }
                    },
                    label = {
                        Text(
                            text = "Child Id",
                            style = AppTheme.BodyBase, // Use a smaller style for the label
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    //  Force the numeric keyboard
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = AppTheme.TitlePageStyle.copy(textAlign = TextAlign.Center)
                )

                // Confirm Button (Styled like your RoleButton)
                Button(
                    onClick = { if (childIdInput.isNotEmpty()) onLinkConfirmed(childIdInput) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("LINK DEVICE", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes Logout to bottom

            // --- BOTTOM SECTION ---
            TextButton(onClick = onLogout) {
                Text(
                    text = "Not your account? Logout",
                    style = AppTheme.BodyBase,
                    textDecoration = TextDecoration.Underline,
                    color = Color.Gray
                )
            }
        }
    }
}



@Composable
fun ParentBottomNavigation(onSettingsClick: () -> Unit) {
    NavigationBar(containerColor = AppTheme.Surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") },
            selected = true,
            onClick = {} // Already on Home
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick // Opens the Logout/Settings Dialog
        )
    }
}

@Composable
fun LinkDeviceDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Child Device") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Child ID") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LogoutDialog(onDismiss: () -> Unit, onLogout: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = { Text("Do you want to log out of your parent account?") },
        confirmButton = {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Error)
            ) {
                Text("Log Out")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- PREVIEWS ---

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun ParentDashboardPreview() {
    // Mock incidents for the statistics card and log table
    val mockIncidents = listOf(
        FirebaseSyncManager.LogEntry("scam_link", "HIGH", "Messenger", System.currentTimeMillis()),
        FirebaseSyncManager.LogEntry("inappropriate_term", "MEDIUM", "Facebook", System.currentTimeMillis() - 3600000),
        FirebaseSyncManager.LogEntry("safe_word", "LOW", "WhatsApp", System.currentTimeMillis() - 7200000)
    )

    ParentDashboardScreen(
        targetId = "882-149", // A mock linked child ID
        incidents = mockIncidents,
        refreshing = false,
        onRefresh = {},
        onHeaderSettingsClick = {},
        onBottomNavSettingsClick = {},
        onDebugResetRole = {} //
    )

}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun SettingsDialogPreview() {
    MaterialTheme {
        // We use a Box with a semi-transparent background to simulate the
        // "dim" effect that occurs when a dialog is open.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            LogoutDialog(
                onDismiss = {},
                onLogout = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LinkDialogPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            LinkDeviceDialog(
                onDismiss = {},
                onConfirm = {}
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun LinkDeviceSetupScreenPreview() {
    MaterialTheme {
        LinkDeviceSetupScreen(
            user = "Parent",
            onLinkConfirmed = {},
            onLogout = {}
        )
    }
}