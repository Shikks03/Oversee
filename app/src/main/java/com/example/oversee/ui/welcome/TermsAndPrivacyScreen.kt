package com.example.oversee.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oversee.ui.theme.AppTheme

@Composable
fun TermsAndPrivacyScreen(onAccept: () -> Unit) {
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // --- HEADER ---
        Icon(Icons.Rounded.Shield, contentDescription = "Privacy", tint = AppTheme.Primary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Privacy Policy &\nUser Agreement", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Your privacy and security are our top priority. Please review how OverSee handles your family's data.", color = Color.Gray, fontSize = 16.sp, lineHeight = 24.sp)

        Spacer(modifier = Modifier.height(32.dp))

        // --- SCROLLABLE TERMS BOX ---
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC) // Light Slate Background
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text("1. Data Collection", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("OverSee collects screen telemetry, accessibility events, and app usage data strictly for parental monitoring purposes. This data is end-to-end encrypted.", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), lineHeight = 20.sp)

                Text("2. Privacy & Security", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("We do not sell, rent, or share your child's data with third-party advertisers. Data is stored securely on Google Firebase and is only accessible by the linked Parent Account.", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), lineHeight = 20.sp)

                Text("3. Device Permissions", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("The child device requires Accessibility, Screen Overlay, and Screen Capture permissions to function. Misuse of these permissions to bypass system security is strictly prohibited.", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), lineHeight = 20.sp)

                Text("4. Account Termination", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("You may delete your account and all associated child data at any time through the Parent Dashboard settings. Upon deletion, all logs are permanently wiped from our servers.", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), lineHeight = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- CHECKBOX & ACTION BUTTON ---
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { isChecked = it },
                colors = CheckboxDefaults.colors(checkedColor = AppTheme.Primary)
            )
            Text("I have read and agree to the Terms of Service and Privacy Policy.", fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAccept,
            enabled = isChecked, // Button is disabled until checkbox is checked!
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary, disabledContainerColor = Color.LightGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Accept & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isChecked) Color.White else Color.DarkGray)
        }
    }
}