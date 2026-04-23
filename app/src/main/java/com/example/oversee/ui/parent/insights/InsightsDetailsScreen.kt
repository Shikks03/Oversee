package com.example.oversee.ui.parent.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

// Component Imports
import com.example.oversee.ui.components.charts.ActivityHeatmap
import com.example.oversee.ui.components.lists.LogItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightDetailsScreen(incidents: List<FirebaseSyncManager.LogEntry>) {
    var selectedDateMillis by remember { mutableLongStateOf(getStartOfDay(System.currentTimeMillis())) }
    var showDatePicker by remember { mutableStateOf(false) }

    val endOfDayMillis = selectedDateMillis + (24 * 60 * 60 * 1000L) - 1
    val dailyIncidents = remember(incidents, selectedDateMillis) {
        incidents.filter { it.timestamp in selectedDateMillis..endOfDayMillis }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppTheme.Background).padding(AppTheme.PaddingDefault),
        verticalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes) // <-- APPLIED THEME PADDING
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Daily Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Jump to date", tint = AppTheme.Primary)
            }
        }

        HorizontalCalendar(selectedDate = selectedDateMillis, onDateSelected = { selectedDateMillis = it })

        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes)) { // <-- APPLIED THEME PADDING
            ActivityHeatmap(dailyIncidents) // The smooth wave chart!

            Text("Detected Content", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (dailyIncidents.isEmpty()) {
                Text("No incidents recorded on this day.", color = Color.Gray, fontSize = 14.sp)
            } else {
                val sortedIncidents = remember(dailyIncidents) {
                    dailyIncidents.sortedWith(
                        compareBy<FirebaseSyncManager.LogEntry> {
                            when (it.severity) { "HIGH" -> 0; "MEDIUM" -> 1; else -> 2 }
                        }.thenByDescending { it.timestamp }
                    )
                }
                Card(shape = RoundedCornerShape(AppTheme.CardCorner), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        sortedIncidents.forEachIndexed { index, incident ->
                            LogItem(incident)
                            if (index < sortedIncidents.size - 1) HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // Material 3 Single Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = getStartOfDay(it) }
                    showDatePicker = false
                }) { Text("Jump") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ---------------- Helper Components for this Screen ----------------

@Composable
private fun HorizontalCalendar(selectedDate: Long, onDateSelected: (Long) -> Unit) {
    val dayInMillis = 24 * 60 * 60 * 1000L
    val days = remember(selectedDate) { (-7..7).map { offset -> selectedDate + (offset * dayInMillis) } }

    val dayOfWeekFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dayOfMonthFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTheme.CardCorner),
        colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
        border = BorderStroke(1.dp, AppTheme.Border)
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.PaddingBoxes),
            state = rememberLazyListState(initialFirstVisibleItemIndex = 4) // Center on the selected date
        ) {
            items(days.size) { index ->
                val dateMillis = days[index]
                val isSelected = dateMillis == selectedDate
                val dayOfWeek = remember(dateMillis) { dayOfWeekFormat.format(Date(dateMillis)) }
                val dayOfMonth = remember(dateMillis) { dayOfMonthFormat.format(Date(dateMillis)) }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDateSelected(dateMillis) }
                        .background(if (isSelected) AppTheme.Primary.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(dayOfMonth, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal, color = if (isSelected) AppTheme.Primary else Color.Black, fontSize = 18.sp)
                    Text(dayOfWeek, color = if (isSelected) AppTheme.Primary else Color.Gray, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// Helper to normalize a timestamp to 12:00 AM of that day
private fun getStartOfDay(timestamp: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}