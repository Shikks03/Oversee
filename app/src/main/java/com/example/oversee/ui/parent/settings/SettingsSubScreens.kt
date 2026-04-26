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
import com.example.oversee.utils.readAssetFile
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
//@Composable
//fun ChangePasswordScreen(onBackClick: () -> Unit) {
//    var current by remember { mutableStateOf("") }
//    var newPass by remember { mutableStateOf("") }
//
//    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
//        SettingsTopBar("Change Password", onBackClick)
//        Column(modifier = Modifier.padding(AppTheme.PaddingDefault), verticalArrangement = Arrangement.spacedBy(16.dp)) {
//            Column {
//                // UPDATED
//                OverSeeTextField(value = current, onValueChange = { current = it }, label = "Current Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
//                TextButton(onClick = { /* TODO: Forgot Password API */ }, modifier = Modifier.align(Alignment.End)) {
//                    Text("Forgot Password?", color = AppTheme.Primary, fontWeight = FontWeight.Bold)
//                }
//            }
//            // UPDATED
//            OverSeeTextField(value = newPass, onValueChange = { newPass = it }, label = "New Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
//            Button(onClick = onBackClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Update Password") }
//        }
//    }
//}


// --- 8. Help & Support ---
@Composable
fun HelpSupportScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    // Loads the manual text from assets/user_manual.txt
    val userManual = remember { context.readAssetFile("user_manual.txt") }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.Background)) {
        SettingsTopBar("Help & Support", onBackClick)

        Column(
            modifier = Modifier
                .padding(horizontal = AppTheme.PaddingDefault)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("User Manual", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppTheme.Primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                border = BorderStroke(1.dp, AppTheme.Border)
            ) {
                Text(
                    text = userManual,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color.DarkGray
                )
            }
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