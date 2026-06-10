package com.example.oversee.ui.parent

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
import com.example.oversee.data.DeviceRepository
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
    children: List<DeviceRepository.ChildDevice>,
    selectedChildFid: String?,
    incidents: List<FirebaseSyncManager.LogEntry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onChildSelected: (String) -> Unit,
    onRenameChild: (String) -> Unit,
    onRemoveChild: (DeviceRepository.ChildDevice) -> Unit,
    onLogoutClick: () -> Unit,
    onDebugResetRole: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(initialTab) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val selectedChild = children.firstOrNull { it.fid == selectedChildFid }
    val targetNickname = selectedChild?.name ?: "Child Device"
    val targetId = selectedChild?.displayUid ?: DeviceRepository.toDisplayCode(selectedChildFid)

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
            in 6..15 -> 3
            5 -> 0
            else -> 0
        }
    }

    Scaffold(
        bottomBar = {
            ParentTabs(
                currentTab = when(currentTab) {
                    5 -> 0
                    in 6..15 -> 3
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
                    targetId = targetId, targetNickname = targetNickname,
                    children = children, selectedChildFid = selectedChildFid, onChildSelected = onChildSelected,
                    incidents = incidents,
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
                    children = children,
                    onRemoveChild = onRemoveChild,
                    onLogoutClick = { showLogoutDialog = true },
                    onDebugResetRole = onDebugResetRole,
                    onSyncHistoryClick = { currentTab = 6 },
                    onHelpSupportClick = { currentTab = 14 },
                    onPrivacyPolicyClick = { currentTab = 15 }
                )
                // Settings Sub-Routes
                6 -> SyncHistoryScreen(onBackClick = { currentTab = 3 }, onManualSyncClick = onRefreshAndExtendRange)
//                8 -> ChangePasswordScreen(onBackClick = { currentTab = 3 })
//                10 -> DeleteAccountScreen(onBackClick = { currentTab = 3 })
//                11 -> PinLockScreen(onBackClick = { currentTab = 3 })
//                12 -> CustomKeywordScreen(onBackClick = { currentTab = 3 })
//                13 -> QuietHoursScreen(onBackClick = { currentTab = 3 })
                14 -> HelpSupportScreen(onBackClick = { currentTab = 3 })
                15 -> PrivacyPolicyScreen(onBackClick = { currentTab = 3 })
            }
        }

        // --- SYNC LOADING POPUP ---
        if (refreshing) {
            SyncLoadingDialog()
        }

        // --- CUSTOM DIALOGS USING THE REUSABLE COMPONENT ---

        if (showEditDialog) {
            EditChildNameDialog(
                currentName = targetNickname,
                onDismiss = { showEditDialog = false },
                onConfirm = { newName ->
                    onRenameChild(newName.ifBlank { "Child Device" })
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

@Composable
private fun EditChildNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    OverSeeDialog(
        title = "Edit Child Name",
        description = "This name is shown on your dashboard and in alerts.",
        confirmText = "Save",
        onConfirm = { onConfirm(name) },
        onDismiss = onDismiss
    ) {
        OverSeeTextField(
            value = name,
            onValueChange = { name = it },
            label = "Child's Name / Nickname",
            modifier = Modifier.fillMaxWidth()
        )
    }
}