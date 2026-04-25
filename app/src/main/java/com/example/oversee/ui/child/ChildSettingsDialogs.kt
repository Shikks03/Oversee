package com.example.oversee.ui.child

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSettingsDialog(
    deviceId: String, accountId: String, parentId: String, parentName: String, lastSyncedTime: String, consoleLogs: List<String>,
    onDismiss: () -> Unit, onChangePin: () -> Unit,
    onExitApp: () -> Unit, onDebugResetRole: () -> Unit
) {
    var showSyncHistory by remember { mutableStateOf(false) }
    var showActivityConsole by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Device Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = AppTheme.ChildBackground
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // --- UPDATED: DEVICE & ACCOUNT INFO ---
                item { Text("Device & Account Info", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.ChildAccent, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            // Moved Device ID here!
                            SettingsRow(icon = Icons.Rounded.Smartphone, title = "This Device ID", subtitle = deviceId, onClick = null)
                            HorizontalDivider(color = AppTheme.ChildBackground)
                            SettingsRow(icon = Icons.Rounded.Tag, title = "Account ID", subtitle = accountId, onClick = null)
                            HorizontalDivider(color = AppTheme.ChildBackground)
                            SettingsRow(icon = Icons.Rounded.Person, title = "Connected Parent", subtitle = parentName, onClick = null)
                            HorizontalDivider(color = AppTheme.ChildBackground)
                            SettingsRow(icon = Icons.Rounded.Badge, title = "Parent ID", subtitle = parentId, onClick = null)
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }

                item { Text("Synchronization", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.ChildAccent, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.History, title = "Sync History", subtitle = "Last synced: $lastSyncedTime", onClick = { showSyncHistory = true })
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Security", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.ChildAccent, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.LockReset, title = "Change PIN", onClick = onChangePin)
                            HorizontalDivider(color = AppTheme.ChildBackground)
                            SettingsRow(icon = Icons.AutoMirrored.Rounded.ExitToApp, title = "Lock & Exit Dashboard", onClick = onExitApp, isDestructive = true)
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Debug & Advanced", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.ChildTextSecondary, modifier = Modifier.padding(start = 8.dp)) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            SettingsRow(icon = Icons.Rounded.Terminal, title = "View Activity Console", onClick = { showActivityConsole = true })
                            HorizontalDivider(color = AppTheme.ChildBackground)
                            SettingsRow(icon = Icons.Rounded.Refresh, title = "Reset Role (Keep ID)", onClick = onDebugResetRole, isDestructive = true)
                        }
                    }
                }
            }
        }
    }

    if (showSyncHistory) SyncHistoryDialog(consoleLogs, onDismiss = { showSyncHistory = false })
    if (showActivityConsole) ActivityConsoleDialog(consoleLogs, onDismiss = { showActivityConsole = false })
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    isLoading: Boolean = false
) {
    val modifier = if (onClick != null) {
        Modifier.clickable(enabled = !isLoading) { onClick() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier.fillMaxWidth().then(modifier).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDestructive) AppTheme.ChildError else AppTheme.ChildTextSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDestructive) AppTheme.ChildError else Color.Black)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = AppTheme.ChildTextSecondary)
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppTheme.ChildAccent)
        } else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

// =========================================================================
// HOW TO USE DIALOG
// =========================================================================
@Composable
fun HowToUseDialog(onDismiss: () -> Unit) {
    OverSeeDialog(
        title = "How to use OverSee",
        description = "Welcome to the Child Dashboard! Here is how this app protects this device:",
        confirmText = "Got it",
        onConfirm = onDismiss,
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(icon = Icons.Rounded.Shield, text = "OverSee runs silently in the background. It monitors activity to ensure digital safety.")
            InfoRow(icon = Icons.Rounded.TouchApp, text = "Keep all Service Status icons green. If an icon turns red, tap it to fix the missing permission.")
            InfoRow(icon = Icons.Rounded.Lock, text = "This dashboard is locked with a Smart PIN to prevent unauthorized changes.")
            InfoRow(icon = Icons.Rounded.CloudSync, text = "Security logs are automatically encrypted and synced to the connected Parent Dashboard.")
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = AppTheme.ChildAccent, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = AppTheme.ChildTextSecondary, lineHeight = 18.sp)
    }
}

// =========================================================================
// EXISTING SUB-COMPONENTS
// =========================================================================

@Composable
fun SyncHistoryDialog(consoleLogs: List<String>, onDismiss: () -> Unit) {
    val syncEvents = remember(consoleLogs) { consoleLogs.filter { it.contains("Sync Event") } }
    OverSeeDialog(title = "Sync History", description = "Recent manual and automated sync events.", confirmText = "Close", onConfirm = onDismiss, onDismiss = onDismiss) {
        if (syncEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("No recent syncs recorded.", color = AppTheme.ChildTextSecondary, fontSize = 14.sp) }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(syncEvents) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.ChildBackground), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudSync, null, tint = AppTheme.ChildAccent, modifier = Modifier.size(20.dp))
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
        val maxDialogHeight = com.example.oversee.ui.theme.Responsive.dialogMaxHeight()
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.ChildBackground)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(consoleLogs) { log ->
                    Text(text = log, color = Color.DarkGray, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

