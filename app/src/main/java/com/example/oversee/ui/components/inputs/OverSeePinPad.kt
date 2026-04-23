package com.example.oversee.ui.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.theme.AppTheme

@Composable
fun OverSeePinPad(
    title: String,
    subtitle: String,
    errorText: String? = null,
    onPinComplete: (String) -> Unit,
    // Optional Custom Bottom Content (like Checkboxes or "Cancel" buttons)
    bottomContent: @Composable (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }

    // Clear PIN automatically if an error is passed in!
    LaunchedEffect(errorText) {
        if (errorText != null) pin = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)) // Slate background
            .padding(top = 80.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- HEADER & DOTS ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            // The 4 iOS-style dots
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (i in 0 until 4) {
                    val isFilled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) Color(0xFF0F766E) else Color.LightGray)
                    )
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(24.dp))
                Text(errorText, color = AppTheme.Error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // --- NUMPAD GRID ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            for (row in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    for (key in row) {
                        if (key == "") {
                            Spacer(modifier = Modifier.size(76.dp))
                        } else if (key == "DEL") {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Backspace, contentDescription = "Delete", tint = Color.DarkGray, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) onPinComplete(pin)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                            }
                        }
                    }
                }
            }

            // --- INJECTED BOTTOM CONTENT ---
            if (bottomContent != null) {
                Spacer(Modifier.height(16.dp))
                bottomContent()
            }
        }
    }
}