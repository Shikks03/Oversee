package com.example.oversee.ui.parent.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.DeviceRepository
import com.example.oversee.ui.components.dialogs.OverSeeDialog
import com.example.oversee.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    children: List<DeviceRepository.ChildDevice>,
    onRemoveChild: (DeviceRepository.ChildDevice) -> Unit,
    onLogoutClick: () -> Unit,
    onDebugResetRole: () -> Unit,
    onSyncHistoryClick: () -> Unit,
    onHelpSupportClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit = {}
) {
    var showRemovePicker by remember { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<DeviceRepository.ChildDevice?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppTheme.PaddingDefault),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // 1. Account & Data
        SettingsGroup("Account") {
            SettingsItem(Icons.AutoMirrored.Filled.ExitToApp, "Log Out", "Sign out of your parent account", isDestructive = true, onClick = onLogoutClick)
        }

        // 2. Device Management
        SettingsGroup("Device Management") {
            SettingsItem(Icons.Default.History, "Syncing History", "View past data synchronizations", onClick = onSyncHistoryClick)
            SettingsItem(Icons.Default.LinkOff, "Remove Child Device", "Stop monitoring and delete a child's data", isDestructive = true, onClick = { showRemovePicker = true })
        }

        // 3. Security & Control
//        SettingsGroup("Security & Control") {
//            SettingsItem(Icons.Default.Lock, "PIN / Biometric Lock", "Require FaceID or PIN to open app", onClick = onPinLockClick)
//            SettingsItem(Icons.Default.FormatListBulleted, "Custom Keyword List", "Add specific words you want flagged", onClick = onKeywordListClick)
//            SettingsItem(Icons.Default.NotificationsOff, "Mute / Quiet Hours", "Pause notifications during specific times", onClick = onQuietHoursClick)
//        }

        // 4. Support & Legal
        SettingsGroup("Support & Legal") {
            SettingsItem(Icons.AutoMirrored.Filled.HelpOutline, "Help & Contact Support", "Get help or report an issue", onClick = onHelpSupportClick)
            SettingsItem(Icons.Default.Gavel, "Privacy Policy & Terms", "Read our data handling policies", onClick = onPrivacyPolicyClick)
        }

        // 5. Developer
        SettingsGroup("Developer") {
            SettingsItem(Icons.Default.BugReport, "Reset Role Selection", "Return to Parent/Child selection", onClick = onDebugResetRole)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showRemovePicker) {
        OverSeeDialog(
            title = "Remove Child Device",
            description = if (children.isEmpty()) "No child devices linked." else "Select the child to remove:",
            confirmText = "Close",
            onConfirm = { showRemovePicker = false },
            onDismiss = { showRemovePicker = false }
        ) {
            Column {
                children.forEach { child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                removeCandidate = child
                                showRemovePicker = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AppTheme.Primary)
                        Text(child.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }

    removeCandidate?.let { candidate ->
        OverSeeDialog(
            title = "Remove ${candidate.name}?",
            description = "This permanently deletes all monitored data for this child. The child's phone will stop syncing.",
            confirmText = "Remove",
            isDestructive = true,
            onConfirm = {
                onRemoveChild(candidate)
                removeCandidate = null
            },
            onDismiss = { removeCandidate = null }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.Primary, modifier = Modifier.padding(start = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (isDestructive) AppTheme.Error else Color.Gray)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (isDestructive) AppTheme.Error else Color.Black)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}
