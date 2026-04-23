package com.example.oversee.ui.parent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.oversee.ui.theme.AppTheme

@Composable
fun ParentTabs(currentTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = AppTheme.Surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Overview") },
            selected = currentTab == 0,
            onClick = { onTabSelected(0) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.CalendarToday, null) },
            label = { Text("Daily") },
            selected = currentTab == 1,
            onClick = { onTabSelected(1) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, null) }, // Changed icon to List
            label = { Text("Logs") },
            selected = currentTab == 2,
            onClick = { onTabSelected(2) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
            selected = currentTab == 3,
            onClick = { onTabSelected(3) }
        )
    }
}