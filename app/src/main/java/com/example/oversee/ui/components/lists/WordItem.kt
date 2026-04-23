package com.example.oversee.ui.components.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.theme.AppTheme

@Composable
fun WordItem(
    word: String,
    count: Int,
    onClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFFFFEBEE), // Light Red
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() } // <-- USED IT HERE
    ) {
        Text(
            text = "$word ($count)",
            color = AppTheme.Error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}