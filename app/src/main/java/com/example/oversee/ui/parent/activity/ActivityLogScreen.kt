package com.example.oversee.ui.parent.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

// Component Imports
import com.example.oversee.ui.components.lists.LogTable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    incidents: List<FirebaseSyncManager.LogEntry>,
    startDate: Long,
    endDate: Long,
    onDateRangeChanged: (Long, Long) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDebugResetRole: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val filteredIncidents = remember(incidents, startDate, endDate) {
        incidents.filter { it.timestamp in startDate..endDate }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(AppTheme.PaddingDefault)) {
        Text("Incident History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Date Range Selector (Syncs with Overview screen!)
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

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (filteredIncidents.isEmpty()) {
                Text("No incidents recorded in this range.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            } else {
                LogTable(filteredIncidents)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            TextButton(onClick = onDebugResetRole, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Debug: Log Out of Role (Keep Link)", color = Color.Gray)
            }
        }

        Button(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (refreshing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Fetch Latest Logs")
            }
        }
    }

    // Material 3 Custom Date Range Dialog
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
            DateRangePicker(
                state = datePickerState, modifier = Modifier.weight(1f),
                title = { Text("Select Data Range", modifier = Modifier.padding(16.dp)) },
                headline = { Text("Filter Logs", modifier = Modifier.padding(horizontal = 16.dp)) },
                showModeToggle = true
            )
        }
    }
}