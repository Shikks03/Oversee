// oversee/ui/parent/settings/SettingsScreen.kt

package com.example.oversee.ui.parent.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    onLogoutClick: () -> Unit,
    onDebugResetRole: () -> Unit,
    onSyncHistoryClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onManualClick: () -> Unit
) {
    var showUnlinkDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppTheme.PaddingDefault),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // 1. Account
        SettingsGroup("Account") {
            SettingsItem(Icons.Default.Person, "Edit Profile", "Change your name or email", onClick = onEditProfileClick)
            SettingsItem(Icons.Default.ExitToApp, "Log Out", "Sign out of your parent account", isDestructive = true, onClick = onLogoutClick)
        }

        // 2. Device Management
        SettingsGroup("Device Management") {
            SettingsItem(Icons.Default.History, "Syncing History", "View past data synchronizations", onClick = onSyncHistoryClick)
            SettingsItem(Icons.Default.LinkOff, "Unlink Device", "Stop monitoring the current child device", isDestructive = true, onClick = { showUnlinkDialog = true })
        }

        // 3. Support & Manual
        SettingsGroup("Help & Information") {
            SettingsItem(Icons.Default.MenuBook, "User Manual", "Read the complete safety guide", onClick = onManualClick)
        }

        // 4. Developer
        SettingsGroup("Developer") {
            SettingsItem(Icons.Default.BugReport, "Reset Role Selection", "Return to Parent/Child selection", onClick = onDebugResetRole)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text("Unlink Device?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to stop monitoring this child device? This will stop all incoming data.") },
            confirmButton = {
                Button(
                    onClick = { showUnlinkDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Error)
                ) { Text("Unlink") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) { Text("Cancel") }
            }
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