package com.example.oversee.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.oversee.ui.theme.AppTheme

@Composable
fun OverSeeDialog(
    title: String,
    description: String? = null,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false, // If true, the confirm button turns red!
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // This allows us to inject custom text fields or toggles right into the middle of the dialog!
    content: @Composable (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppTheme.Surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // --- 1. TITLE ---
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )

                // --- 2. OPTIONAL DESCRIPTION ---
                if (description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                // --- 3. OPTIONAL CUSTOM INJECTED CONTENT ---
                if (content != null) {
                    Spacer(Modifier.height(24.dp))
                    content() // This is where the TextFields will go if provided!
                } else {
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(24.dp))

                // --- 4. ACTION BUTTONS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) AppTheme.Error else AppTheme.Primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}