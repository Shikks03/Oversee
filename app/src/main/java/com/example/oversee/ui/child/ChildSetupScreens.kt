
package com.example.oversee.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.components.inputs.OverSeePinPad
import com.example.oversee.ui.theme.AppTheme
import androidx.compose.ui.platform.LocalContext
import com.example.oversee.data.local.AppPreferenceManager
import java.security.MessageDigest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.oversee.ui.components.inputs.OverSeeTextField

// --- ADDED: Child-proof obscure security questions ---
val SECURITY_QUESTIONS = listOf(
    "What was the exact make and model of your first car?",
    "Who was your favorite band or artist in the 8th grade?",
    "What was the name of the company where you had your very first paying job?",
    "What was the name of the bank that issued your first credit card?",
    "What was the name of the street you lived on when you were 10 years old?"
)

// --- ADDED: Secure Hashing ---
fun hashSecurityAnswer(answer: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(answer.trim().lowercase().toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

// =========================================================================
// 1. CHILD LINK SETUP SCREEN
// =========================================================================
@Composable
fun ChildLinkSetupScreen(deviceId: String, onLinkConfirmed: () -> Unit, onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(AppTheme.ChildBackground).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(64.dp), tint = AppTheme.ChildAccent)

            Text("Link this Device", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)

            Text(
                "Open the OverSee app on your Parent device and enter the code below to link this phone.",
                fontSize = 15.sp, textAlign = TextAlign.Center, color = AppTheme.ChildTextSecondary, lineHeight = 22.sp
            )

            // Huge Device ID Display
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Text(
                    text = deviceId,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = AppTheme.ChildAccent,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onLinkConfirmed,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.ChildAccent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("I have linked this device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            TextButton(onClick = onLogout) {
                Text("Cancel & Logout", color = AppTheme.ChildTextSecondary)
            }
        }
    }
}

// =========================================================================
// 2. SMART PIN SETUP FLOW (Updated Layout)
// =========================================================================
@Composable
fun SmartPinSetupFlow(parentPin: String, onPinSaved: (String, Boolean) -> Unit) {
    var stage by remember { mutableStateOf(if (parentPin.isNotBlank()) "ASK_PARENT" else "CREATE_NEW") }
    var tempPin by remember { mutableStateOf("") }
    var errorTxt by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    when (stage) {
        "ASK_PARENT" -> {
            OverSeePinPad(
                title = "Use Parent PIN?",
                subtitle = "An existing Parent Dashboard PIN was found. Enter it to secure this device as well.",
                errorText = errorTxt,
                onPinComplete = { entered ->
                    if (entered == parentPin) onPinSaved(entered, false) else errorTxt = "Incorrect Parent PIN."
                },
                bottomContent = {
                    TextButton(onClick = { stage = "CREATE_NEW" }) { Text("Create a Different PIN Instead", color = AppTheme.ChildAccent, fontWeight = FontWeight.Bold) }
                }
            )
        }
        "CREATE_NEW" -> {
            OverSeePinPad(
                title = "Create PIN",
                subtitle = "Set a 4-digit PIN to secure this dashboard from being tampered with.",
                onPinComplete = { entered ->
                    tempPin = entered
                    stage = "CONFIRM_NEW"
                },
                bottomContent = {
                    // This invisible spacer replaces the deleted checkbox so the keypad doesn't shift down!
                    Spacer(modifier = Modifier.height(48.dp))
                }
            )
        }
        "CONFIRM_NEW" -> {
            OverSeePinPad(
                title = "Confirm PIN",
                subtitle = "Re-enter your 4-digit PIN to confirm.",
                errorText = errorTxt,
                onPinComplete = { entered ->
                    if (entered == tempPin) {
                        errorTxt = null
                        stage = "SETUP_SECURITY"
                    } else {
                        errorTxt = "PINs do not match. Try again."
                        stage = "CREATE_NEW"
                    }
                },
                bottomContent = {
                    TextButton(onClick = { stage = "CREATE_NEW"; errorTxt = null }) { Text("Start Over", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
                }
            )
        }
        "SETUP_SECURITY" -> {
            SecurityQuestionsSetupScreen(
                onComplete = { q1, a1, q2, a2 ->
                    AppPreferenceManager.saveString(context, "sec_q1", q1)
                    AppPreferenceManager.saveString(context, "sec_a1_hash", hashSecurityAnswer(a1))
                    AppPreferenceManager.saveString(context, "sec_q2", q2)
                    AppPreferenceManager.saveString(context, "sec_a2_hash", hashSecurityAnswer(a2))

                    onPinSaved(tempPin, false)
                },
                onCancel = { stage = "CREATE_NEW" }
            )
        }
    }
}

// =========================================================================
// 2B. SECURITY QUESTIONS SETUP
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionsSetupScreen(
    onComplete: (String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var q1 by remember { mutableStateOf(SECURITY_QUESTIONS[0]) }
    var a1 by remember { mutableStateOf("") }

    var q2 by remember { mutableStateOf(SECURITY_QUESTIONS[1]) }
    var a2 by remember { mutableStateOf("") }

    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.ChildBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(56.dp), tint = AppTheme.ChildAccent)
        Spacer(Modifier.height(16.dp))
        Text("PIN Recovery Setup", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
        Text(
            "Select two questions your child is unlikely to know. These will be used if you ever forget your PIN.",
            fontSize = 14.sp, textAlign = TextAlign.Center, color = AppTheme.ChildTextSecondary, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Question 1
        QuestionDropdown(selected = q1, options = SECURITY_QUESTIONS, onSelect = { q1 = it }, label = "Question 1")
        Spacer(Modifier.height(8.dp))
        OverSeeTextField(value = a1, onValueChange = { a1 = it }, label = "Answer", modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        // Question 2
        QuestionDropdown(selected = q2, options = SECURITY_QUESTIONS, onSelect = { q2 = it }, label = "Question 2")
        Spacer(Modifier.height(8.dp))
        OverSeeTextField(value = a2, onValueChange = { a2 = it }, label = "Answer", modifier = Modifier.fillMaxWidth())

        if (showError) {
            Spacer(Modifier.height(16.dp))
            Text("Please select two distinct questions and answer both.", color = AppTheme.ChildError, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (q1 == q2 || a1.isBlank() || a2.isBlank()) {
                    showError = true
                } else {
                    showError = false
                    onComplete(q1, a1, q2, a2)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.ChildAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Finish Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel", color = AppTheme.ChildTextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDropdown(selected: String, options: List<String>, onSelect: (String) -> Unit, label: String) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp, lineHeight = 18.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// =========================================================================
// 3. PERMISSIONS SETUP SCREENS
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsSetupScreen(checks: Map<String, Boolean>, onFixPermission: (String) -> Unit, onCheckAgain: () -> Unit, onDebugSkip: () -> Unit) {
    val allGood = checks["Accessibility"] == true && checks["Overlay"] == true
    Scaffold(
        containerColor = AppTheme.ChildBackground,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, null, tint = AppTheme.ChildAccent) }
                Spacer(Modifier.width(20.dp))
                Text(text = "Required Permissions", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "OverSee needs these permissions to monitor the device in the background.", fontSize = 15.sp, textAlign = TextAlign.Center, color = AppTheme.ChildTextSecondary, lineHeight = 22.sp)
            Spacer(Modifier.height(36.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
                PermissionGridItem(title = "Accessibility", icon = Icons.Rounded.Visibility, isGranted = checks["Accessibility"] == true, modifier = Modifier.weight(1f), onClick = { onFixPermission("Accessibility") })
                PermissionGridItem(title = "Overlay", icon = Icons.Rounded.Layers, isGranted = checks["Overlay"] == true, modifier = Modifier.weight(1f), onClick = { onFixPermission("Overlay") })
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onCheckAgain, enabled = allGood, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (allGood) AppTheme.ChildAccent else Color.LightGray), shape = RoundedCornerShape(16.dp)) {
                Text(text = if (allGood) "START MONITORING" else "COMPLETE SETUP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDebugSkip) { Text("Debug: Skip Permissions", color = AppTheme.ChildTextSecondary, fontSize = 13.sp) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionGridItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isGranted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.aspectRatio(0.75f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(60.dp).background(if (isGranted) AppTheme.ChildSuccess.copy(alpha = 0.1f) else AppTheme.ChildAccentLight, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = if (isGranted) AppTheme.ChildSuccess else AppTheme.ChildAccent, modifier = Modifier.size(30.dp)) }
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, lineHeight = 18.sp)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isGranted) AppTheme.ChildSuccess else AppTheme.ChildAccent), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                Text(text = if (isGranted) "Granted" else "Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
        }
    }
}
// =========================================================================
// 4. SECURITY QUESTION RECOVERY SCREEN
// =========================================================================
@Composable
fun SecurityQuestionRecoveryScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Load the saved questions and hashed answers
    val savedQ1 = remember { AppPreferenceManager.getString(context, "sec_q1", "") }
    val savedA1Hash = remember { AppPreferenceManager.getString(context, "sec_a1_hash", "") }
    val savedQ2 = remember { AppPreferenceManager.getString(context, "sec_q2", "") }
    val savedA2Hash = remember { AppPreferenceManager.getString(context, "sec_a2_hash", "") }

    var a1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.ChildBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(56.dp), tint = AppTheme.ChildAccent)
        Spacer(Modifier.height(16.dp))
        Text("PIN Recovery", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
        Text(
            "Answer your security questions to reset the dashboard PIN.",
            fontSize = 14.sp, textAlign = TextAlign.Center, color = AppTheme.ChildTextSecondary, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Question 1
        Text(savedQ1, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        OverSeeTextField(value = a1, onValueChange = { a1 = it }, label = "Answer", modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        // Question 2
        Text(savedQ2, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        OverSeeTextField(value = a2, onValueChange = { a2 = it }, label = "Answer", modifier = Modifier.fillMaxWidth())

        if (showError) {
            Spacer(Modifier.height(16.dp))
            Text("One or both answers are incorrect.", color = AppTheme.ChildError, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                // Hash the current inputs and compare them to the saved hashes!
                if (hashSecurityAnswer(a1) == savedA1Hash && hashSecurityAnswer(a2) == savedA2Hash) {
                    showError = false
                    onSuccess() // Correct! Let them reset the PIN.
                } else {
                    showError = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.ChildAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Verify & Reset PIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel", color = AppTheme.ChildTextSecondary)
        }
    }
}