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
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseUserManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth


class ParentDashboardActivity : ComponentActivity() {

    // --- STATE MANAGEMENT ---
    private val TAG = "ParentDashboard"
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
                val targetNickname = remember {
                    AppPreferenceManager.getString(this@ParentDashboardActivity, "target_nickname", "Child Device")
                }

                val fid = childFid.value
                if (fid == null) {
                    ChildNotLinkedScreen(
                        onLogout = { performLogout() },
                        onCheckAgain = { loadChildFid() }
                    )
                } else {
                    ParentDashboardScreen(
                        targetId = fid,
                        targetNickname = targetNickname,
                        incidents = incidentList,
                        refreshing = isRefreshing.value,
                        onRefresh = { refreshDashboard() },
                        onLogoutClick = { performLogout() },
                        onDebugResetRole = { debugResetRole() }
                    )
                }
            }
        }
    }

    // --- DATA LOGIC ---

    private fun loadChildFid(autoRefresh: Boolean = false) {
        val uid = AuthRepository.getUserId() ?: run {
            Log.e(TAG, "DIAG L1: uid is null — user not authenticated")
            return
        }
        Log.d(TAG, "DIAG L1: fetching FID pointers for uid=$uid")
        FirebaseUserManager.fetchDeviceFidPointers(uid) { _, cFid ->
            Log.d(TAG, "DIAG L2: childFid from Firestore = $cFid")
            childFid.value = cFid
            if (cFid != null) {
                fetchLogs(cFid)
                if (autoRefresh) refreshDashboard()
            } else {
                Log.w(TAG, "DIAG L2: childFid is null — child device not linked yet")
            }
        }
    }

    private fun fetchLogs(fid: String) {
        Log.d(TAG, "DIAG L3: fetchLogs called with fid=$fid")
        IncidentRepository.fetchRecentIncidents(
            context = this,
            childFid = fid,
            onSuccess = { logs ->
                Log.d(TAG, "DIAG L4: fetchLogs returned ${logs.size} entries")
                incidentList.clear()
                incidentList.addAll(logs)
            },
            onError = { error ->
                Log.e(TAG, "DIAG L4: fetchLogs error: $error")
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun refreshDashboard() {
        val fid = childFid.value ?: return
        Log.d(TAG, "DIAG L3: refreshDashboard called with fid=$fid")
        isRefreshing.value = true
        IncidentRepository.fetchRecentIncidents(
            context = this,
            childFid = fid,
            onSuccess = { logs ->
                Log.d(TAG, "DIAG L4: refreshDashboard returned ${logs.size} entries")
                incidentList.clear()
                incidentList.addAll(logs)
                isRefreshing.value = false
            },
            onError = { error ->
                Log.e(TAG, "DIAG L4: refreshDashboard error: $error")
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
fun ChildNotLinkedScreen(onLogout: () -> Unit, onCheckAgain: () -> Unit) {
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
            Button(
                onClick = onCheckAgain,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check Again", fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.TextButton(onClick = onLogout) {
                Text("Not your account? Logout", color = Color.Gray)
            }
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun ChildNotLinkedScreenPreview() {
    MaterialTheme {
        ChildNotLinkedScreen(onLogout = {}, onCheckAgain = {})
    }
}
