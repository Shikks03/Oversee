package com.example.oversee.ui.components.lists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogTable(incidents: List<FirebaseSyncManager.LogEntry>) {
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val grouped = remember(incidents) {
        incidents.groupBy { dateFormat.format(Date(it.timestamp)).uppercase() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        grouped.forEach { (date, logs) ->
            Text(text = date, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.Gray, fontWeight = FontWeight.ExtraBold)
            Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, AppTheme.Border), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), modifier = Modifier.padding(bottom = 16.dp)) {
                Column {
                    TableHeader()
                    logs.forEach { incident ->
                        LogItem(incident = incident) // Uses default empty click
                        if (incident != logs.last()) HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(AppTheme.ColumnGap), verticalAlignment = Alignment.CenterVertically) {
        Text("Severity", Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Word", Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("App", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Time", Modifier.weight(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun LogItem(
    incident: FirebaseSyncManager.LogEntry,
    onClick: () -> Unit = {}
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeString = remember(incident.timestamp) { timeFormat.format(Date(incident.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.ColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeverityBadge(severity = incident.severity, modifier = Modifier.weight(0.8f))
        Text(text = incident.word, modifier = Modifier.weight(1.2f), fontSize = 12.sp, maxLines = 1)
        Text(text = incident.app, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
        Text(text = timeString, modifier = Modifier.weight(0.8f), fontSize = 12.sp, textAlign = TextAlign.End)
    }
}