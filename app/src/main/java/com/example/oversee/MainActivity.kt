package com.example.oversee

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// --- PROJECT SPECIFIC IMPORTS ---
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseSyncManager
import com.example.oversee.data.remote.FirebaseUserManager
import com.example.oversee.ui.child.ChildDashboardRoute
import com.example.oversee.ui.parent.ParentDashboardScreen
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.ui.welcome.AuthScreen
import com.example.oversee.ui.welcome.RoleSelectionScreen
import com.example.oversee.ui.welcome.TermsAndPrivacyScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRouter()
            }
        }
    }
}

@Composable
fun AppRouter() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var userName by remember { mutableStateOf("") }

    NavHost(
        navController = navController,
        startDestination = "splash",
        enterTransition = { slideInHorizontally(animationSpec = tween(500), initialOffsetX = { fullWidth: Int -> fullWidth }) + fadeIn(tween(500)) },
        exitTransition = { slideOutHorizontally(animationSpec = tween(500), targetOffsetX = { fullWidth: Int -> -fullWidth / 3 }) + fadeOut(tween(500)) },
        popEnterTransition = { slideInHorizontally(animationSpec = tween(500), initialOffsetX = { fullWidth: Int -> -fullWidth / 3 }) + fadeIn(tween(500)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(500), targetOffsetX = { fullWidth: Int -> fullWidth }) + fadeOut(tween(500)) }
    ) {

        // 1. SPLASH / LOADING ROUTE
        composable("splash") {
            LaunchedEffect(Unit) {
                val termsAccepted = AppPreferenceManager.getBoolean(context, "terms_accepted", false)

                if (!termsAccepted) {
                    navController.navigate("terms") { popUpTo("splash") { inclusive = true } }
                } else {
                    if (AuthRepository.isUserLoggedIn()) {
                        val uid = AuthRepository.getUserId()
                        if (uid != null) {
                            UserRepository.refreshLocalProfile(context, uid) {
                                val role = UserRepository.getLocalRole(context)
                                userName = UserRepository.getLocalName(context)
                                when (role) {
                                    "PARENT" -> navController.navigate("parent_dashboard") { popUpTo("splash") { inclusive = true } }
                                    "CHILD" -> navController.navigate("child_dashboard") { popUpTo("splash") { inclusive = true } }
                                    else -> navController.navigate("role_selection") { popUpTo("splash") { inclusive = true } }
                                }
                            }
                        } else {
                            navController.navigate("auth") { popUpTo("splash") { inclusive = true } }
                        }
                    } else {
                        navController.navigate("auth") { popUpTo("splash") { inclusive = true } }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(AppTheme.PrimaryGradient), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // 2. TERMS AND PRIVACY ROUTE
        composable(
            route = "terms",
            enterTransition = { fadeIn(tween(500)) },
            exitTransition = { fadeOut(tween(500)) }
        ) {
            TermsAndPrivacyScreen(
                onAccept = {
                    AppPreferenceManager.saveBoolean(context, "terms_accepted", true)
                    navController.navigate("auth") { popUpTo("terms") { inclusive = true } }
                }
            )
        }

        // 3. AUTH ROUTE
        composable(
            route = "auth",
            exitTransition = {
                if (targetState.destination.route == "role_selection") fadeOut(tween(500)) else null
            }
        ) {
            AuthScreen(
                onSignIn = { email, pass ->
                    if (email.isBlank() || pass.isBlank()) {
                        Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                    } else {
                        AuthRepository.signIn(context, email, pass) { success, error ->
                            if (success) {
                                userName = UserRepository.getLocalName(context)
                                navController.navigate("role_selection") { popUpTo("auth") { inclusive = true } }
                            } else {
                                Toast.makeText(context, "Sign In Failed: $error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onSignUp = { name, email, pass, confirmPass ->
                    if (name.isBlank() || email.isBlank() || pass.isBlank() || confirmPass.isBlank()) {
                        Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    } else if (pass != confirmPass) {
                        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    } else {
                        AuthRepository.register(context, name, email, pass) { success, error ->
                            if (success) {
                                userName = UserRepository.getLocalName(context)
                                navController.navigate("role_selection") { popUpTo("auth") { inclusive = true } }
                            } else {
                                Toast.makeText(context, "Registration Failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onResetPassword = { email ->
                    if (email.isBlank()) {
                        Toast.makeText(context, "Please enter your email address", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // 4. ROLE SELECTION ROUTE
        composable(
            route = "role_selection",
            enterTransition = {
                if (initialState.destination.route == "auth") fadeIn(tween(500)) else null
            },
            exitTransition = {
                if (targetState.destination.route == "parent_dashboard" || targetState.destination.route == "child_dashboard") {
                    fadeOut(tween(500)) + scaleOut(targetScale = 1.1f, animationSpec = tween(500))
                } else null
            }
        ) {
            // --- NEW: STATE FOR THE TRANSFER DIALOG ---
            var showTransferDialog by remember { mutableStateOf(false) }
            var pendingNewFid by remember { mutableStateOf<String?>(null) }
            var existingOldFid by remember { mutableStateOf<String?>(null) }

            RoleSelectionScreen(
                user = userName,
                onSelectChild = {
                    val uid = AuthRepository.getUserId() ?: return@RoleSelectionScreen
                    DeviceRepository.getFid { newFid ->
                        if (newFid != null) {
                            // 1. Check if the parent already has a child linked
                            FirebaseUserManager.fetchDeviceFidPointers(uid) { _, existingChildFid ->
                                if (existingChildFid != null && existingChildFid != newFid) {
                                    // 2. An old device exists! Trigger the pop-up.
                                    existingOldFid = existingChildFid
                                    pendingNewFid = newFid
                                    showTransferDialog = true
                                } else {
                                    // 3. No old device exists. Just link it normally.
                                    DeviceRepository.setRoleForThisDevice(context, uid, newFid, "CHILD") { success ->
                                        if (success) navController.navigate("child_dashboard") { popUpTo("role_selection") { inclusive = true } }
                                    }
                                }
                            }
                        }
                    }
                },
                onSelectParent = {
                    val uid = AuthRepository.getUserId() ?: return@RoleSelectionScreen
                    DeviceRepository.getFid { fid ->
                        if (fid != null) {
                            DeviceRepository.setRoleForThisDevice(context, uid, fid, "PARENT") { success ->
                                if (success) navController.navigate("parent_dashboard") { popUpTo("role_selection") { inclusive = true } }
                            }
                        }
                    }
                }
            )

            // --- NEW: THE TRANSFER CONFIRMATION POP-UP ---
            if (showTransferDialog) {
                // Re-using your excellent OverSeeDialog component!
                com.example.oversee.ui.components.dialogs.OverSeeDialog(
                    title = "Device Already Linked",
                    description = "This account already has an existing child device linked. Do you want to transfer data and link this device instead?\n\n(Old logs will be securely deleted).",
                    confirmText = "Transfer & Link",
                    dismissText = "Cancel",
                    isDestructive = false,
                    onConfirm = {
                        showTransferDialog = false
                        val uid = AuthRepository.getUserId()
                        if (uid != null && pendingNewFid != null && existingOldFid != null) {
                            // 1. Wipe the old Ghost Data to save server costs
                            com.example.oversee.data.remote.FirebaseIncidentManager.deleteOldChildData(existingOldFid!!)

                            // 2. Link this new device
                            DeviceRepository.setRoleForThisDevice(context, uid, pendingNewFid!!, "CHILD") { success ->
                                if (success) navController.navigate("child_dashboard") { popUpTo("role_selection") { inclusive = true } }
                            }
                        }
                    },
                    onDismiss = { showTransferDialog = false }
                )
            }
        }

        // 5. PARENT DASHBOARD ROUTE (Real Firebase Logic Added)
        composable(
            route = "parent_dashboard",
            enterTransition = {
                if (initialState.destination.route == "role_selection") {
                    fadeIn(tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = tween(500))
                } else null
            }
        ) {
            var childFid by remember { mutableStateOf<String?>(null) }
            val incidents = remember { mutableStateListOf<FirebaseSyncManager.LogEntry>() }
            var isRefreshing by remember { mutableStateOf(false) }

            // Permission Launcher for Android 13+ Notifications
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            val loadData = {
                isRefreshing = true
                val uid = AuthRepository.getUserId()
                if (uid != null) {
                    FirebaseUserManager.fetchDeviceFidPointers(uid) { _, cFid ->
                        childFid = cFid
                        if (cFid != null) {
                            IncidentRepository.fetchRecentIncidents(context, cFid, onSuccess = { logs ->
                                incidents.clear()
                                incidents.addAll(logs)
                                isRefreshing = false
                            }, onError = { error ->
                                isRefreshing = false
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            })
                        } else {
                            isRefreshing = false
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                loadData()
            }

            if (childFid == null) {
                ChildNotLinkedScreen(
                    onLogout = {
                        AuthRepository.logout(context)
                        navController.navigate("auth") { popUpTo(0) }
                    },
                    onCheckAgain = { loadData() }
                )
            } else {
                ParentDashboardScreen(
                    targetId = childFid ?: "",
                    targetNickname = AppPreferenceManager.getString(context, "target_nickname", "Child Device"),
                    incidents = incidents,
                    refreshing = isRefreshing,
                    onRefresh = { loadData() },
                    onLogoutClick = {
                        AuthRepository.logout(context)
                        navController.navigate("auth") { popUpTo(0) }
                    },
                    onDebugResetRole = {
                        val uid = AuthRepository.getUserId()
                        if (uid != null) {
                            UserRepository.clearLocalRole(context)
                            navController.navigate("role_selection") { popUpTo(0) }
                        }
                    }
                )
            }
        }

        // 6. CHILD DASHBOARD ROUTE
        composable(
            route = "child_dashboard",
            enterTransition = {
                if (initialState.destination.route == "role_selection") {
                    fadeIn(tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = tween(500))
                } else null
            }
        ) {
            ChildDashboardRoute(
                onLogoutClick = {
                    AuthRepository.logout(context)
                    navController.navigate("auth") { popUpTo(0) }
                },
                onDebugResetRole = {
                    val uid = AuthRepository.getUserId()
                    if (uid != null) {
                        UserRepository.clearLocalRole(context)
                        navController.navigate("role_selection") { popUpTo(0) }
                    }
                }
            )
        }
    }
}

// --- EXTRACTED FROM PARENT DASHBOARD ACTIVITY ---
@Composable
fun ChildNotLinkedScreen(onLogout: () -> Unit, onCheckAgain: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(AppTheme.Surface).padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(80.dp), tint = AppTheme.Primary)
            Text(text = "Set up your child's phone", style = AppTheme.TitlePageStyle, textAlign = TextAlign.Center)
            Text(
                text = "Install OverSee on your child's phone and sign in with the same account. When prompted, choose \"This is my child's phone\".",
                style = AppTheme.BodyBase, textAlign = TextAlign.Center, color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check Again", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onLogout) {
                Text("Not your account? Logout", color = Color.Gray)
            }
        }
    }
}