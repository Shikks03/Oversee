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
// 2. SMART PIN SETUP FLOW
// =========================================================================
@Composable
fun SmartPinSetupFlow(parentPin: String, onPinSaved: (String, Boolean) -> Unit) {
    var stage by remember { mutableStateOf(if (parentPin.isNotBlank()) "ASK_PARENT" else "CREATE_NEW") }
    var tempPin by remember { mutableStateOf("") }
    var errorTxt by remember { mutableStateOf<String?>(null) }
    var syncParentPin by remember { mutableStateOf(false) }

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
                    if (parentPin.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { syncParentPin = !syncParentPin }) {
                            Checkbox(checked = syncParentPin, onCheckedChange = { syncParentPin = it }, colors = CheckboxDefaults.colors(checkedColor = AppTheme.ChildAccent))
                            Text("Use this PIN for Parent Dashboard too", fontSize = 14.sp, color = Color.DarkGray)
                        }
                    }
                }
            )
        }
        "CONFIRM_NEW" -> {
            OverSeePinPad(
                title = "Confirm PIN",
                subtitle = "Re-enter your 4-digit PIN to confirm.",
                errorText = errorTxt,
                onPinComplete = { entered ->
                    if (entered == tempPin) onPinSaved(entered, syncParentPin)
                    else {
                        errorTxt = "PINs do not match. Try again."
                        stage = "CREATE_NEW"
                    }
                },
                bottomContent = {
                    TextButton(onClick = { stage = "CREATE_NEW"; errorTxt = null }) { Text("Start Over", color = AppTheme.ChildTextSecondary, fontWeight = FontWeight.Bold) }
                }
            )
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
    Card(modifier = modifier.aspectRatio(0.85f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(64.dp).background(if (isGranted) AppTheme.ChildSuccess.copy(alpha = 0.1f) else AppTheme.ChildAccentLight, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = if (isGranted) AppTheme.ChildSuccess else AppTheme.ChildAccent, modifier = Modifier.size(32.dp)) }
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isGranted) AppTheme.ChildSuccess else AppTheme.ChildAccent), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) {
                Text(text = if (isGranted) "Granted" else "Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}