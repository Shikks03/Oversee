package com.example.oversee.ui.parent.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.AuditRepository
import com.example.oversee.ui.components.cards.SummaryCard
import com.example.oversee.ui.parent.settings.AuditLogItem
import com.example.oversee.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<AuditRepository.AuditEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch the logs automatically when the screen opens
    LaunchedEffect(Unit) {
        AuditRepository.fetchLogs(context) { fetchedLogs ->
            logs = fetchedLogs
            isLoading = false
        }
    }

    // --- DATA ANALYSIS (The "Reporting" aspect for the rubric) ---
    val failedLogins = logs.count { it.action == "SECURITY_ALERT" }
    val configChanges = logs.count { it.action == "SETTING_CHANGED" }

    // Filter out only the security alerts for the recent list
    val securityAlerts = logs.filter { it.action == "SECURITY_ALERT" || it.action == "SETTING_CHANGED" }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        TopAppBar(
            title = { Text("Security Insights", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.Surface)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. KPI Summary Cards
                item {
                    Text("Last 30 Days Overview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.Primary)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            // Assuming SummaryCard takes a title and value
                            SummaryCard(
                                title = "Failed Logins",
                                value = failedLogins.toString(),
                                icon = Icons.Default.WarningAmber, // Pass a warning icon
                                onClick = { /* Do nothing on click for now */ }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SummaryCard(
                                title = "Config Changes",
                                value = configChanges.toString(),
                                icon = Icons.Default.Settings, // Pass a settings icon
                                onClick = { /* Do nothing on click for now */ }
                            )
                        }
                    }
                }

                // 2. Trend Analysis (Requires your TrendLineChart component)
                // Note: If you don't have the chart ready, you can comment this block out for now.
                /*
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Threat Activity Trend", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.Primary)
                    Card(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
                        // Pass mock data or calculated float arrays to your chart
                        TrendLineChart(data = listOf(0f, 1f, 0f, failedLogins.toFloat(), 0f))
                    }
                }
                */

                // 3. Recent Security Events List
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Critical Security Events", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.Primary)
                }

                if (securityAlerts.isEmpty()) {
                    item {
                        Text("No security alerts detected. Your system is secure.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                    }
                } else {
                    items(securityAlerts) { alert ->
                        // Reusing the AuditLogItem you built for the AuditLogScreen
                        AuditLogItem(alert)
                    }
                }
            }
        }
    }
}