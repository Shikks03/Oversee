// oversee/ui/parent/settings/AuditLogScreen.kt
package com.example.oversee.ui.parent.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.AuditRepository
import com.example.oversee.data.AuthRepository
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AuditLogScreen(onBackClick: () -> Unit) {
    var logs by remember { mutableStateOf<List<AuditRepository.AuditEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current // <<< 1. ADD THIS

    LaunchedEffect(Unit) {
        // <<< 2. UPDATE THIS FUNCTION CALL
        AuditRepository.fetchLogs(context) { fetchedLogs ->
            logs = fetchedLogs
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("System Audit Logs", onBackClick)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.Primary)
            }
        } else if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No audit logs found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.PaddingDefault),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs) { log ->
                    AuditLogItem(log)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditRepository.AuditEvent) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
    val timeString = dateFormat.format(Date(log.timestamp))

    val (icon, tint) = when (log.action) {
        "USER_LOGIN" -> Icons.Default.Login to AppTheme.Success
        "SETTING_CHANGED" -> Icons.Default.Settings to AppTheme.Primary
        else -> Icons.Default.Laptop to Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                Spacer(Modifier.height(4.dp))
                Text(log.details, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeString, fontSize = 11.sp, color = Color.Gray)
                    Text("Device: ${log.device}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}