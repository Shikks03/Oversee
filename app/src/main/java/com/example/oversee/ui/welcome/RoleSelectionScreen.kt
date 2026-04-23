package com.example.oversee.ui.welcome

// --- JETPACK COMPOSE UI ---
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// --- PROJECT SPECIFIC ---
import com.example.oversee.ui.theme.AppTheme

@Composable
fun RoleSelectionScreen(
    user: String,
    onSelectChild: () -> Unit,
    onSelectParent: () -> Unit
) {
    // --- ANIMATION STATE ---
    var startAnimation by remember { mutableStateOf(false) }

    // Trigger the animation shortly after the screen loads
    LaunchedEffect(Unit) {
        delay(150) // Wait for the Android activity slide transition to finish
        startAnimation = true
    }

    // 1. Animate the Bottom Sheet Height (Starts at 40%, grows to 75%)
    val sheetFraction by animateFloatAsState(
        targetValue = if (startAnimation) 0.75f else 0.40f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "SheetHeightFraction"
    )

    // 2. Animate the Top Icon Size (Shrinks slightly to make room)
    val iconSize by animateDpAsState(
        targetValue = if (startAnimation) 64.dp else 100.dp,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "IconSize"
    )

    // 3. Animate Content Opacity (Fades in the cards once the sheet is moving)
    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200, easing = LinearEasing),
        label = "ContentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.PrimaryGradient)
    ) {
        // --- TOP LOGO / WELCOME SECTION ---
        // Dynamically adjusts its height based on what the bottom sheet isn't using
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f - sheetFraction)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhonelinkSetup,
                contentDescription = "Setup",
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val displayName = if (user.isNotBlank()) user else "there"
            Text(
                text = "Hello, $displayName!",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Let's get this device set up.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // --- BOTTOM CARD SECTION ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetFraction) // This drives the upward slide!
                .align(Alignment.BottomCenter),
            color = AppTheme.Surface,
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .graphicsLayer(alpha = contentAlpha), // Fades content in
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Who is using this device?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Left
                )

                // Parent Role Card
                RoleSelectionCard(
                    title = "Parent Device",
                    description = "Monitor activity, manage settings, and receive alerts from your phone.",
                    icon = Icons.Default.Person,
                    themeColor = AppTheme.Primary,
                    onClick = onSelectParent
                )

                // Child Role Card
                RoleSelectionCard(
                    title = "Child Device",
                    description = "Enable protection and background monitoring on your child's phone.",
                    icon = Icons.Default.Face,
                    themeColor = AppTheme.Success,
                    onClick = onSelectChild
                )
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    themeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Icon Box
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(themeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = themeColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chevron Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = themeColor
            )
        }
    }
}

// --- PREVIEW ---
@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6", name = "Role Selection Screen")
@Composable
fun RoleSelectionScreenPreview() {
    MaterialTheme {
        RoleSelectionScreen(
            user = "Admin User",
            onSelectChild = {},
            onSelectParent = {}
        )
    }
}