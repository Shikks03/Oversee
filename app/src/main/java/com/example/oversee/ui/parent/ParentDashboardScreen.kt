package com.example.oversee.ui.parent

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

// --- PROJECT DATA & SERVICES ---
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme

// --- COMPONENTS & SUB-SCREENS ---
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.components.inputs.OverSeeTextField
import com.example.oversee.ui.parent.home.DashboardOverviewScreen
import com.example.oversee.ui.parent.insights.InsightDetailsScreen
import com.example.oversee.ui.parent.activity.ActivityLogScreen
import com.example.oversee.ui.parent.notifications.NotificationsScreen
import com.example.oversee.ui.parent.settings.*

@Composable
fun ParentDashboardScreen(
    targetId: String,
    targetNickname: String,
    incidents: List<FirebaseSyncManager.LogEntry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLogoutClick: () -> Unit,
    onDebugResetRole: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(initialTab) }

    // Dialog States
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Live State for Target Nickname/ID so it updates immediately when edited
    var liveTargetId by remember { mutableStateOf(targetId) }
    var liveTargetNickname by remember { mutableStateOf(targetNickname) }

    val defaultEnd = remember { System.currentTimeMillis() }
    val defaultStart = remember { defaultEnd - (7 * 24 * 60 * 60 * 1000L) }
    var sharedStartDate by remember { mutableLongStateOf(defaultStart) }
    var sharedEndDate by remember { mutableLongStateOf(defaultEnd) }

    val onRefreshAndExtendRange = remember(onRefresh) {
        {
            sharedEndDate = System.currentTimeMillis()
            onRefresh()
        }
    }

    BackHandler(enabled = currentTab != 0) {
        currentTab = when (currentTab) {
            in 6..14 -> 3
            5 -> 0
            else -> 0
        }
    }

    Scaffold(
        bottomBar = {
            ParentTabs(
                currentTab = when(currentTab) {
                    5 -> 0
                    in 6..14 -> 3
                    else -> currentTab
                },
                onTabSelected = { currentTab = it }
            )
        },
        containerColor = AppTheme.Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                0 -> DashboardOverviewScreen(
                    targetId = liveTargetId, targetNickname = liveTargetNickname, incidents = incidents,
                    startDate = sharedStartDate, endDate = sharedEndDate, refreshing = refreshing,
                    onRefresh = onRefreshAndExtendRange, onDateRangeChanged = { start, end -> sharedStartDate = start; sharedEndDate = end },
                    onEditClick = { showEditDialog = true },
                    onNavigateToLogs = { currentTab = 2 }, onNotificationClick = { currentTab = 5 }
                )
                1 -> InsightDetailsScreen(incidents = incidents)
                2 -> ActivityLogScreen(
                    incidents = incidents, startDate = sharedStartDate, endDate = sharedEndDate,
                    onDateRangeChanged = { start, end -> sharedStartDate = start; sharedEndDate = end },
                    refreshing = refreshing, onRefresh = onRefreshAndExtendRange, onDebugResetRole = onDebugResetRole
                )
                3 -> SettingsScreen(
                    onLogoutClick = { showLogoutDialog = true }, onDebugResetRole = onDebugResetRole, onSyncHistoryClick = { currentTab = 6 },
                    onEditProfileClick = { currentTab = 7 }, onChangePasswordClick = { currentTab = 8 },
                    onExportDataClick = { currentTab = 9 }, onDeleteAccountClick = { currentTab = 10 },
                    onPinLockClick = { currentTab = 11 }, onKeywordListClick = { currentTab = 12 },
                    onQuietHoursClick = { currentTab = 13 }, onHelpSupportClick = { currentTab = 14 }
                )
                5 -> NotificationsScreen(incidents = incidents, onBackClick = { currentTab = 0 }, onNotificationClick = { clickedIncident ->
                    val cal = Calendar.getInstance().apply { timeInMillis = clickedIncident.timestamp }
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); sharedStartDate = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); sharedEndDate = cal.timeInMillis
                    currentTab = 2
                })
                // Settings Sub-Routes
                6 -> SyncHistoryScreen(onBackClick = { currentTab = 3 }, onManualSyncClick = onRefreshAndExtendRange)
                7 -> EditProfileScreen(onBackClick = { currentTab = 3 })
                8 -> ChangePasswordScreen(onBackClick = { currentTab = 3 })
                9 -> ExportDataScreen(onBackClick = { currentTab = 3 })
                10 -> DeleteAccountScreen(onBackClick = { currentTab = 3 })
                11 -> PinLockScreen(onBackClick = { currentTab = 3 })
                12 -> CustomKeywordScreen(onBackClick = { currentTab = 3 })
                13 -> QuietHoursScreen(onBackClick = { currentTab = 3 })
                14 -> HelpSupportScreen(onBackClick = { currentTab = 3 })
            }
        }

        // --- SYNC LOADING POPUP ---
        if (refreshing) {
            SyncLoadingDialog()
        }

        // --- CUSTOM DIALOGS USING THE REUSABLE COMPONENT ---

        if (showEditDialog) {
            EditChildDeviceDialog(
                currentId = if (liveTargetId == "NOT_LINKED") "" else liveTargetId,
                currentNickname = if (liveTargetId == "NOT_LINKED") "" else liveTargetNickname,
                onDismiss = { showEditDialog = false },
                onConfirm = { newNickname: String, newId: String ->
                    val finalNick = newNickname.ifBlank { "Child Device" }
                    AppPreferenceManager.saveString(context, "target_nickname", finalNick)
                    liveTargetNickname = finalNick

                    if (newId != liveTargetId && newId.isNotBlank()) {
                        val uid = AuthRepository.getUserId()
                        if (uid != null) {
                            UserRepository.linkChildDevice(context, uid, newId) { success ->
                                if (success) {
                                    liveTargetId = newId
                                    onRefresh()
                                    Toast.makeText(context, "Device Linked Successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to link device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    showEditDialog = false
                }
            )
        }

        if (showLogoutDialog) {
            OverSeeDialog(
                title = "Log Out",
                description = "Are you sure you want to log out of your parent account? You will need to sign back in to view child data.",
                confirmText = "Log Out",
                isDestructive = true,
                onConfirm = { showLogoutDialog = false; onLogoutClick() },
                onDismiss = { showLogoutDialog = false }
            )
        }
    }
}

@Composable
private fun SyncLoadingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = AppTheme.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = AppTheme.Primary, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                Text("Syncing", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Fetching latest data from the child device...",
                    fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// --- CLEANED UP EDIT DEVICE DIALOG ---
@Composable
private fun EditChildDeviceDialog(
    currentId: String,
    currentNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var deviceId by remember { mutableStateOf(currentId) }
    val isNewLink = currentId.isBlank()

    // Pass the TextFields directly into our new reusable OverSeeDialog!
    OverSeeDialog(
        title = if (isNewLink) "Link Child Device" else "Edit Child Device",
        description = "Enter your child's 6-digit ID to connect their device to your dashboard.",
        confirmText = if (isNewLink) "Link Device" else "Save Changes",
        onConfirm = { onConfirm(nickname, deviceId) },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OverSeeTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = "Child's Name / Nickname",
                modifier = Modifier.fillMaxWidth()
            )
            OverSeeTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = "Child's 6-Digit ID",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}