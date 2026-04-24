package com.example.oversee.ui.parent.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.ui.components.inputs.OverSeeTextField // NEW IMPORT
import com.example.oversee.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

// --- 0. Syncing History ---
@Composable
fun SyncHistoryScreen(onBackClick: () -> Unit, onManualSyncClick: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Syncing History", onBackClick)
        Column(modifier = Modifier.padding(horizontal = AppTheme.PaddingDefault)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), border = BorderStroke(1.dp, AppTheme.Primary.copy(alpha = 0.3f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = "Info", tint = AppTheme.Primary)
                    Spacer(Modifier.width(12.dp))
                    Text("The app syncs when manually synced from the dashboard or if a high-risk event is detected.", fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onManualSyncClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)) {
                Icon(Icons.Default.Sync, contentDescription = "Sync Now", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Force Manual Sync", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Recent Sync Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(6) { index -> SyncHistoryItem(isManual = index % 2 == 0) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun SyncHistoryItem(isManual: Boolean) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
    val timeString = dateFormat.format(Date(System.currentTimeMillis() - ((Math.random() * 86400000).toLong())))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isManual) "Manual Sync" else "High-Risk Auto Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isManual) Color.Black else AppTheme.Error)
                Spacer(Modifier.height(4.dp))
                Text(timeString, fontSize = 12.sp, color = Color.Gray)
            }
            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = AppTheme.Success, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Success", color = AppTheme.Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- 1. Edit Profile ---
@Composable
fun EditProfileScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(AppPreferenceManager.getString(context, "parent_name", "Parent User")) }
    val originalEmail = AppPreferenceManager.getString(context, "parent_email", "parent@example.com")
    var email by remember { mutableStateOf(originalEmail) }

    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showOtpPrompt by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Edit Profile", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // UPDATED
            OverSeeTextField(value = name, onValueChange = { name = it }, label = "Full Name", modifier = Modifier.fillMaxWidth())
            OverSeeTextField(value = email, onValueChange = { email = it }, label = "Email Address", modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    AppPreferenceManager.saveString(context, "parent_name", name)
                    if (email != originalEmail) showPasswordPrompt = true else onBackClick()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
            ) { Text("Save Changes") }
        }
    }

    if (showPasswordPrompt) {
        AlertDialog(
            onDismissRequest = { showPasswordPrompt = false },
            title = { Text("Verify Identity", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Please enter your current password to change your email address.", fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    // UPDATED
                    OverSeeTextField(value = passwordInput, onValueChange = { passwordInput = it }, label = "Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { showPasswordPrompt = false; showOtpPrompt = true }) { Text("Verify") } },
            dismissButton = { TextButton(onClick = { showPasswordPrompt = false }) { Text("Cancel") } }
        )
    }

    if (showOtpPrompt) {
        AlertDialog(
            onDismissRequest = { showOtpPrompt = false },
            title = { Text("Enter OTP", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("A 6-digit code has been sent to $email.", fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    // UPDATED
                    OverSeeTextField(value = otpInput, onValueChange = { otpInput = it }, label = "6-Digit OTP", modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    AppPreferenceManager.saveString(context, "parent_email", email)
                    showOtpPrompt = false
                    onBackClick()
                }) { Text("Confirm Change") }
            },
            dismissButton = { TextButton(onClick = { showOtpPrompt = false }) { Text("Cancel") } }
        )
    }
}

// --- 2. Change Password ---
@Composable
fun ChangePasswordScreen(onBackClick: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Change Password", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                // UPDATED
                OverSeeTextField(value = current, onValueChange = { current = it }, label = "Current Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { /* TODO: Forgot Password API */ }, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot Password?", color = AppTheme.Primary, fontWeight = FontWeight.Bold)
                }
            }
            // UPDATED
            OverSeeTextField(value = newPass, onValueChange = { newPass = it }, label = "New Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = onBackClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Update Password") }
        }
    }
}

// --- 3. Export Data ---
@Composable
fun ExportDataScreen(onBackClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Export Data", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Download a copy of all incident logs and analytics.", color = Color.Gray)
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Export as CSV") }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("Export as PDF") }
        }
    }
}

// --- 4. Delete Account ---
@Composable
fun DeleteAccountScreen(onBackClick: () -> Unit) {
    var confirmation by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Delete Account", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), border = BorderStroke(1.dp, AppTheme.Error)) {
                Text("Warning: This action is permanent and will delete all child monitoring data.", color = AppTheme.Error, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            // UPDATED
            OverSeeTextField(value = confirmation, onValueChange = { confirmation = it }, label = "Type 'DELETE' to confirm", modifier = Modifier.fillMaxWidth())
            Button(onClick = {}, enabled = confirmation == "DELETE", modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Error), shape = RoundedCornerShape(12.dp)) { Text("Permanently Delete Account") }
        }
    }
}

// --- 5. PIN / Biometric Lock ---
@Composable
fun PinLockScreen(
    onBackClick: () -> Unit,
    initialPinEnabled: Boolean? = null,
    initialBiometricsEnabled: Boolean? = null
) {
    val context = LocalContext.current
    var isPinEnabled by remember { mutableStateOf(initialPinEnabled ?: AppPreferenceManager.getBoolean(context, "pin_enabled", false)) }
    var isBiometricsEnabled by remember { mutableStateOf(initialBiometricsEnabled ?: AppPreferenceManager.getBoolean(context, "biometrics_enabled", false)) }
    var pin by remember { mutableStateOf(AppPreferenceManager.getString(context, "saved_pin", "")) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("App Lock", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(24.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Require PIN", fontWeight = FontWeight.Bold)
                    Text("Ask for PIN when opening OverSee", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = isPinEnabled, onCheckedChange = {
                    isPinEnabled = it
                    AppPreferenceManager.saveBoolean(context, "pin_enabled", it)
                    if (!it) {
                        isBiometricsEnabled = false
                        AppPreferenceManager.saveBoolean(context, "biometrics_enabled", false)
                    }
                })
            }

            if (isPinEnabled) {
                // UPDATED
                OverSeeTextField(value = pin, onValueChange = { pin = it }, label = "Enter 4-Digit PIN", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Unlock with Biometrics", fontWeight = FontWeight.Bold)
                        Text("Use Face ID or Fingerprint", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = isBiometricsEnabled, onCheckedChange = {
                        isBiometricsEnabled = it
                        AppPreferenceManager.saveBoolean(context, "biometrics_enabled", it)
                    })
                }
                Button(onClick = {
                    AppPreferenceManager.saveString(context, "saved_pin", pin)
                    onBackClick()
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Save Security Settings") }
            }
        }
    }
}

// --- 6. Custom Keyword List ---
@Composable
fun CustomKeywordScreen(
    onBackClick: () -> Unit,
    initialKeywords: List<String>? = null
) {
    val context = LocalContext.current
    var newWord by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val savedKeywords = remember { AppPreferenceManager.getStringSet(context, "custom_keywords", setOf("bullying", "vape", "address")).toList() }
    val keywords = remember { mutableStateListOf(*(initialKeywords ?: savedKeywords).toTypedArray()) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Custom Keywords", onBackClick)
        Column(modifier = Modifier.padding(horizontal = AppTheme.PaddingDefault)) {
            Text("Add words you want flagged specifically for this device.", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // UPDATED
                OverSeeTextField(
                    value = newWord, onValueChange = { newWord = it; errorMessage = null },
                    label = "Add word", modifier = Modifier.weight(1f), isError = errorMessage != null
                )
                Button(
                    onClick = {
                        val wordToAdd = newWord.trim().lowercase()
                        if (wordToAdd.isNotBlank()) {
                            if (keywords.contains(wordToAdd)) {
                                errorMessage = "This keyword already exists."
                            } else {
                                keywords.add(0, wordToAdd)
                                AppPreferenceManager.saveStringSet(context, "custom_keywords", keywords.toSet())
                                newWord = ""
                            }
                        }
                    },
                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Add") }
            }
            if (errorMessage != null) {
                Text(errorMessage!!, color = AppTheme.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }

            Spacer(Modifier.height(32.dp))
            Text("Existing Keywords", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (keywords.isEmpty()) {
                    item { Text("No custom keywords added yet.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
                } else {
                    items(keywords.size) { index ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(keywords[index], fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    keywords.removeAt(index)
                                    AppPreferenceManager.saveStringSet(context, "custom_keywords", keywords.toSet())
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppTheme.Error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 7. Mute / Quiet Hours ---
@Composable
fun QuietHoursScreen(onBackClick: () -> Unit, initialEnabled: Boolean? = null) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(initialEnabled ?: AppPreferenceManager.getBoolean(context, "quiet_hours_enabled", false)) }
    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

    val savedDaysStr = remember { AppPreferenceManager.getStringSet(context, "quiet_hours_days", setOf("1","2","3","4","5")).map { it.toInt() } }
    val selectedDays = remember { mutableStateListOf(*savedDaysStr.toTypedArray()) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Quiet Hours", onBackClick)
        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Enable Quiet Hours", fontWeight = FontWeight.Bold)
                    Text("Pause notifications during this schedule", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = isEnabled, onCheckedChange = {
                    isEnabled = it
                    AppPreferenceManager.saveBoolean(context, "quiet_hours_enabled", it)
                })
            }
            if (isEnabled) {
                Text("Applies to Days", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    daysOfWeek.forEachIndexed { index, day ->
                        val isSelected = selectedDays.contains(index)
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) AppTheme.Primary else Color(0xFFE0E0E0))
                                .clickable {
                                    if (isSelected) selectedDays.remove(index) else selectedDays.add(index)
                                    AppPreferenceManager.saveStringSet(context, "quiet_hours_days", selectedDays.map { it.toString() }.toSet())
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(day, color = if (isSelected) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold) }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Start Time")
                            Text("10:00 PM", fontWeight = FontWeight.Bold, color = AppTheme.Primary, modifier = Modifier.clickable { }.padding(8.dp))
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("End Time")
                            Text("7:00 AM", fontWeight = FontWeight.Bold, color = AppTheme.Primary, modifier = Modifier.clickable { }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- 8. Help & Support ---
@Composable
fun HelpSupportScreen(onBackClick: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Help & Support", onBackClick)
        Column(modifier = Modifier.padding(horizontal = AppTheme.PaddingDefault).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // UPDATED: Now uses the global wrapper with the leading icon!
            OverSeeTextField(
                value = searchQuery, onValueChange = { searchQuery = it }, placeholder = "Search for help...",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frequently Asked Questions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTheme.Primary)
                HelpCard("How does the app sync data?", "The child device sends data automatically when a high-risk alert happens, or manually when requested via the dashboard.")
                HelpCard("Can my child uninstall the app?", "If device administrator permissions are granted during setup, the app requires a parent PIN to uninstall.")
                HelpCard("How do I link a second child?", "Currently, OverSee supports one active link per parent dashboard. Unlink the current device to switch.")
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Still need help?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Surface, contentColor = Color.Black), border = BorderStroke(1.dp, AppTheme.Border)) {
                    Icon(Icons.Outlined.Chat, contentDescription = "Chat", tint = AppTheme.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Live Chat Support")
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Surface, contentColor = Color.Black), border = BorderStroke(1.dp, AppTheme.Border)) {
                    Icon(Icons.Outlined.Email, contentDescription = "Email", tint = AppTheme.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Email Support Team")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HelpCard(question: String, answer: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppTheme.Surface), border = BorderStroke(1.dp, AppTheme.Border)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(answer, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
        }
    }
}