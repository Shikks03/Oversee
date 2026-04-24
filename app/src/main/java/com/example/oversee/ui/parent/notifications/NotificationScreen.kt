package com.example.oversee.ui.parent.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.utils.MockData
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationsScreen(
    incidents: List<FirebaseSyncManager.LogEntry>,
    onBackClick: () -> Unit,
    onNotificationClick: (FirebaseSyncManager.LogEntry) -> Unit // NEW PARAMETER
) {
    // Filter for high-severity alerts to act as "notifications"
    val notifications = remember(incidents) {
        incidents.filter { it.severity == "HIGH" }.sortedByDescending { it.timestamp }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text("Notifications", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No new notifications.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.PaddingDefault),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { incident ->
                    // Pass the click listener to the item
                    NotificationItem(incident, onNotificationClick)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun NotificationItem(
    incident: FirebaseSyncManager.LogEntry,
    onClick: (FirebaseSyncManager.LogEntry) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }
    val timeString = remember(incident.timestamp) { timeFormat.format(Date(incident.timestamp)) }

    Card(
        // Activate the click listener!
        modifier = Modifier.fillMaxWidth().clickable { onClick(incident) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Warning", tint = AppTheme.Error, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("High Severity Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTheme.Error)
                Spacer(Modifier.height(4.dp))
                Text("Flagged content '${incident.word}' detected on ${incident.app}.", fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(timeString, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6", name = "Notifications Screen")
@Composable
fun NotificationsScreenPreview() {
    NotificationsScreen(
        incidents = MockData.getIncidents(),
        onBackClick = {},
        onNotificationClick = {}
    )
}