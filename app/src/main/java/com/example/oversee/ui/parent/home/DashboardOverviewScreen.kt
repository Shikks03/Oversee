package com.example.oversee.ui.parent.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

// Component Imports
import com.example.oversee.ui.components.cards.SummaryCard
import com.example.oversee.ui.components.charts.TrendLineChart
import com.example.oversee.ui.components.lists.LogItem
import com.example.oversee.ui.components.lists.WordItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardOverviewScreen(
    targetId: String,
    targetNickname: String,
    incidents: List<FirebaseSyncManager.LogEntry>,
    startDate: Long,
    endDate: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDateRangeChanged: (Long, Long) -> Unit,
    onEditClick: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNotificationClick: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val filteredIncidents = remember(incidents, startDate, endDate) {
        incidents.filter { it.timestamp in startDate..endDate }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val lastSyncTime = remember(incidents) { incidents.maxByOrNull { it.timestamp }?.timestamp }
    val highAlertsCount = remember(incidents) { incidents.count { it.severity == "HIGH" } }

    Column(
        modifier = Modifier.fillMaxSize().padding(AppTheme.PaddingDefault).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes) // <-- APPLIED THEME PADDING
    ) {
        // --- TOP APP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onNotificationClick) {
                    Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = AppTheme.Primary)
                }
                if (highAlertsCount > 0) {
                    Box(modifier = Modifier.padding(top = 12.dp, end = 12.dp).size(10.dp).background(AppTheme.Error, CircleShape))
                }
            }
        }

        // --- EMPTY STATE ROUTING ---
        if (targetId == "NOT_LINKED" || targetId.isBlank()) {
            Spacer(Modifier.height(40.dp))
            UnlinkedDashboardState(onLinkDeviceClick = onEditClick)
        } else {
            // --- EXISTING DASHBOARD LOGIC ---
            CompactHeaderRow(
                targetNickname = targetNickname,
                targetId = targetId,
                lastSyncTime = lastSyncTime,
                refreshing = refreshing,
                onEditClick = onEditClick,
                onRefreshClick = onRefresh
            )

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                border = BorderStroke(1.dp, AppTheme.Border)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = "Date Range", tint = AppTheme.Primary)
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Selected Range", fontSize = 12.sp, color = Color.Gray)
                        Text("${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}", fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit Range", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            Text("Activity Trend", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            TrendLineChart(incidents = filteredIncidents, startDate = startDate, endDate = endDate)

            // APPLIED THEME PADDING HERE FOR THE 2 SUMMARY CARDS
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes)) {
                val peakTimeStr = remember(filteredIncidents) {
                    if (filteredIncidents.isEmpty()) "N/A"
                    else {
                        val calendar = Calendar.getInstance()
                        val hourCounts = filteredIncidents.groupingBy {
                            calendar.timeInMillis = it.timestamp
                            calendar.get(Calendar.HOUR_OF_DAY)
                        }.eachCount()

                        val maxHour = hourCounts.maxByOrNull { it.value }?.key ?: 0
                        val amPm = if (maxHour >= 12) "PM" else "AM"
                        val displayHour = if (maxHour % 12 == 0) 12 else maxHour % 12
                        "$displayHour:00 $amPm"
                    }
                }

                SummaryCard(modifier = Modifier.weight(1f), title = "Peak Activity", value = peakTimeStr, icon = Icons.Default.Schedule, onClick = onNavigateToLogs)
                SummaryCard(modifier = Modifier.weight(1f), title = "Total Reports", value = filteredIncidents.size.toString(), icon = Icons.Default.NotificationsActive, onClick = onNavigateToLogs)
            }

            val topWords = remember(filteredIncidents) { filteredIncidents.groupingBy { it.word }.eachCount().entries.sortedByDescending { it.value }.take(3) }
            if (topWords.isNotEmpty()) {
                Text("Top Flagged Words", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        topWords.forEach { entry -> WordItem(entry.key, entry.value) }
                    }
                }
            }

            Text("Recent Activity", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            RecentActivityFeed(filteredIncidents, onNavigateToLogs)

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = startDate, initialSelectedEndDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (datePickerState.selectedStartDateMillis != null && datePickerState.selectedEndDateMillis != null) {
                        onDateRangeChanged(datePickerState.selectedStartDateMillis!!, datePickerState.selectedEndDateMillis!! + (24 * 60 * 60 * 1000L) - 1)
                    }
                    showDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = datePickerState, modifier = Modifier.weight(1f), title = { Text("Select Data Range", modifier = Modifier.padding(16.dp)) }, showModeToggle = true)
        }
    }
}

@Composable
fun UnlinkedDashboardState(onLinkDeviceClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(120.dp).background(AppTheme.Primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LinkOff, contentDescription = null, tint = AppTheme.Primary, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text("No Device Linked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(12.dp))
        Text(
            "Link your child's device using their unique 6-digit ID to start monitoring their activity and protecting them online.",
            fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onLinkDeviceClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Link Child Device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun CompactHeaderRow(
    targetNickname: String,
    targetId: String,
    lastSyncTime: Long?,
    refreshing: Boolean,
    onEditClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val syncText = remember(lastSyncTime) {
        if (lastSyncTime != null) {
            val timeString = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(lastSyncTime))
            "Synced: $timeString"
        } else "Waiting for sync..."
    }

    // APPLIED THEME PADDING HERE FOR THE TOP 2 CARDS
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes)) {
        // 1. Child Device Card (Blue)
        Card(
            modifier = Modifier
                .weight(1f)
                .height(84.dp)
                .clickable { onEditClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.Primary)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(targetNickname, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text("ID: $targetId", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(AppTheme.Success, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(syncText, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 2. Sync Card (White)
        Card(
            modifier = Modifier.size(84.dp).clickable(enabled = !refreshing) { onRefreshClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
            border = BorderStroke(1.dp, AppTheme.Border)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppTheme.Primary)
                } else {
                    Icon(Icons.Rounded.Sync, contentDescription = "Sync", tint = AppTheme.Primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun RecentActivityFeed(incidents: List<FirebaseSyncManager.LogEntry>, onNavigateToLogs: () -> Unit) {
    val top3 = remember(incidents) { incidents.sortedByDescending { it.timestamp }.take(3) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), shape = RoundedCornerShape(AppTheme.CardCorner), border = BorderStroke(1.dp, AppTheme.Border)) {
        Column {
            if (top3.isEmpty()) {
                Text("No recent activity.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 12.sp)
            } else {
                top3.forEachIndexed { index, incident ->
                    LogItem(incident)
                    if (index < top3.size - 1) HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).clickable { onNavigateToLogs() }.padding(12.dp), contentAlignment = Alignment.Center) {
                Text("View All Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.Primary)
            }
        }
    }
}