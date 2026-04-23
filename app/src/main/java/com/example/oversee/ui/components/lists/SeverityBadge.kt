package com.example.oversee.ui.components.lists

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.oversee.ui.theme.AppTheme

@Composable
fun SeverityBadge(severity: String, modifier: Modifier = Modifier) {
    val (bg, text) = when (severity.uppercase()) {
        "HIGH" -> Color(0xFFFFEBEE) to AppTheme.Error
        "MEDIUM" -> Color(0xFFFFF3E0) to AppTheme.Warning
        else -> Color(0xFFE8F5E9) to AppTheme.Success
    }
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(AppTheme.BadgeCorner)) {
        Text(
            text = severity.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}