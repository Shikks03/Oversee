package com.example.oversee.ui.parent

// --- ANDROID & CORE ---
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

// --- JETPACK COMPOSE UI ---
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*

// --- FIREBASE & DATA ---
import com.example.oversee.data.remote.FirebaseSyncManager

// --- PROJECT SPECIFIC ---
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.ui.welcome.RoleSelectionActivity
import com.example.oversee.ui.welcome.SignInActivity

// --- UTILS ---
import java.text.SimpleDateFormat
import java.util.*
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.remote.FirebaseUserManager
import com.google.firebase.auth.FirebaseAuth


class ParentDashboardActivity : ComponentActivity() {

    // --- STATE MANAGEMENT ---
    private val childFid = mutableStateOf<String?>(null)
    private var incidentList = mutableStateListOf<FirebaseSyncManager.LogEntry>()
    private var isRefreshing = mutableStateOf(false)

    // --- NOTIFICATION PERMISSION ---
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    // --- LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val autoRefresh = intent.getBooleanExtra("auto_refresh", false)
        loadChildFid(autoRefresh)

        setContent {
            MaterialTheme {
                var showLogoutDialog by remember { mutableStateOf(false) }

                val fid = childFid.value
                if (fid == null) {
                    ChildNotLinkedScreen(
                        onLogout = { performLogout() }
                    )
                } else {
                    ParentDashboardScreen(
                        incidents = incidentList,
                        refreshing = isRefreshing.value,
                        onRefresh = { refreshDashboard() },
                        onBottomNavSettingsClick = { showLogoutDialog = true },
                        onDebugResetRole = { debugResetRole() }
                    )
                }

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

    private fun loadChildFid(autoRefresh: Boolean = false) {
        val uid = AuthRepository.getUserId() ?: return
        FirebaseUserManager.fetchDeviceFidPointers(uid) { _, cFid ->
            childFid.value = cFid
            if (cFid != null) {
                fetchLogs(cFid)
                if (autoRefresh) refreshDashboard()
            }
        }
    }

    private fun fetchLogs(fid: String) {
        IncidentRepository.fetchRecentIncidents(
            context = this,
            childFid = fid,
            onSuccess = { logs ->
                incidentList.clear()
                incidentList.addAll(logs)
            },
            onError = { error ->
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun refreshDashboard() {
        val fid = childFid.value ?: return
        isRefreshing.value = true
        IncidentRepository.fetchRecentIncidents(
            context = this,
            childFid = fid,
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
        AuthRepository.logout(this)
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun debugResetRole() {
        UserRepository.clearLocalRole(this)
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }
}

// --- COMPOSE UI SCREENS ---

@Composable
fun ChildNotLinkedScreen(onLogout: () -> Unit) {
    val hPad = com.example.oversee.ui.theme.Responsive.horizontalPadding()
    val vPad = com.example.oversee.ui.theme.Responsive.verticalPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.Surface)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = AppTheme.Primary
            )
            Text(
                text = "Set up your child's phone",
                style = AppTheme.TitlePageStyle,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Install OverSee on your child's phone and sign in with the same account. When prompted, choose \"This is my child's phone\".",
                style = AppTheme.BodyBase,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onLogout) {
                Text("Not your account? Logout", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ParentDashboardScreen(
    incidents: List<FirebaseSyncManager.LogEntry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
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
                        Text("Child Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Status: Active", color = AppTheme.Success, fontSize = 12.sp)
                    }
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
fun ParentBottomNavigation(onSettingsClick: () -> Unit) {
    NavigationBar(containerColor = AppTheme.Surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") },
            selected = true,
            onClick = {}
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick
        )
    }
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
    val mockIncidents = listOf(
        FirebaseSyncManager.LogEntry("scam_link", "HIGH", "Messenger", System.currentTimeMillis()),
        FirebaseSyncManager.LogEntry("inappropriate_term", "MEDIUM", "Facebook", System.currentTimeMillis() - 3600000),
        FirebaseSyncManager.LogEntry("safe_word", "LOW", "WhatsApp", System.currentTimeMillis() - 7200000)
    )

    ParentDashboardScreen(
        incidents = mockIncidents,
        refreshing = false,
        onRefresh = {},
        onBottomNavSettingsClick = {},
        onDebugResetRole = {}
    )
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun ChildNotLinkedScreenPreview() {
    MaterialTheme {
        ChildNotLinkedScreen(onLogout = {})
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsDialogPreview() {
    MaterialTheme {
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
