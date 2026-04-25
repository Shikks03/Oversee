package com.example.oversee.ui.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.BorderStroke // Add this if it's missing too

import com.example.oversee.ui.components.inputs.OverSeeTextField
import com.example.oversee.ui.theme.AppTheme

// --- STATE MACHINE ENUM ---
enum class AuthMode {
    SIGN_IN, SIGN_UP, FORGOT_PASSWORD
}

@Composable
fun AuthScreen(
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String) -> Unit,
    onResetPassword: (String) -> Unit
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(AppTheme.PrimaryGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP LOGO SECTION ---
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val isRecoveryMode = authMode == AuthMode.FORGOT_PASSWORD

                AnimatedContent(
                    targetState = isRecoveryMode,
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)))
                            .togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 1.2f, animationSpec = tween(400)))
                    },
                    label = "IconAnimation"
                ) { isRecovery ->
                    Icon(
                        modifier = Modifier.size(100.dp),
                        imageVector = if (isRecovery) Icons.Default.Lock else Icons.Default.VerifiedUser,
                        contentDescription = "App Icon",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = isRecoveryMode,
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400)))
                            .togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 1.1f, animationSpec = tween(400)))
                    },
                    label = "TitleAnimationTop"
                ) { isRecovery ->
                    Text(
                        text = if (isRecovery) "Account Recovery" else "OverSee",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- BOTTOM CARD SECTION ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppTheme.Surface,
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = authMode,
                        transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(400))) },
                        label = "BottomTitleAnimation",
                    ) { mode ->
                        Text(
                            text = when(mode) {
                                AuthMode.SIGN_IN -> "Welcome Back"
                                AuthMode.SIGN_UP -> "Sign Up"
                                AuthMode.FORGOT_PASSWORD -> "Reset Password"
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left
                        )
                    }

                    AnimatedVisibility(
                        visible = authMode == AuthMode.FORGOT_PASSWORD,
                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(400)) + fadeIn(tween(400)),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(400)) + fadeOut(tween(400))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Enter your email to receive a password reset link.",
                                fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Left, lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // THE ACCEPTED LOSS WARNING
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                border = BorderStroke(1.dp, AppTheme.Error)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AppTheme.Error, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Security Warning: For your privacy, your child's data is encrypted with your password. Resetting your password will permanently delete all historical logs. New logs will begin syncing after you log in.",
                                        fontSize = 12.sp, color = AppTheme.Error, lineHeight = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(
                        visible = authMode == AuthMode.SIGN_UP,
                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(400)) + fadeIn(tween(400)),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(400)) + fadeOut(tween(400))
                    ) {
                        Column {
                            OverSeeTextField(
                                value = name, onValueChange = { name = it },
                                label = "Name",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    OverSeeTextField(
                        value = email, onValueChange = { email = it },
                        label = "Email Address",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(
                        visible = authMode != AuthMode.FORGOT_PASSWORD,
                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(400)) + fadeIn(tween(400)),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(400)) + fadeOut(tween(400))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OverSeeTextField(
                                value = password, onValueChange = { password = it },
                                label = "Password",
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )

                            AnimatedVisibility(
                                visible = authMode == AuthMode.SIGN_UP,
                                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(400)) + fadeIn(tween(400)),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(400)) + fadeOut(tween(400))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OverSeeTextField(
                                        value = confirmPassword, onValueChange = { confirmPassword = it },
                                        label = "Confirm Password",
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = authMode == AuthMode.SIGN_IN,
                                modifier = Modifier.align(Alignment.End),
                                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(400)) + fadeIn(tween(400)),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(400)) + fadeOut(tween(400))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Forgot Password?", color = AppTheme.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                        modifier = Modifier.clickable { authMode = AuthMode.FORGOT_PASSWORD }.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            when (authMode) {
                                AuthMode.SIGN_IN -> onSignIn(email, password)
                                AuthMode.SIGN_UP -> onSignUp(name, email, password, confirmPassword)
                                AuthMode.FORGOT_PASSWORD -> {
                                    onResetPassword(email)
                                    authMode = AuthMode.SIGN_IN // Kick them back to sign in after sending
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
                    ) {
                        AnimatedContent(
                            targetState = authMode,
                            transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(400))) },
                            label = "ButtonTextAnimation"
                        ) { mode ->
                            Text(
                                text = when(mode) {
                                    AuthMode.SIGN_IN -> "Sign In"
                                    AuthMode.SIGN_UP -> "Create Account"
                                    AuthMode.FORGOT_PASSWORD -> "Send Reset Link"
                                },
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .clickable {
                                authMode = if (authMode == AuthMode.SIGN_UP) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = authMode,
                            transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(400))) },
                            label = "ToggleTextAnimation"
                        ) { mode ->
                            Row {
                                if (mode == AuthMode.FORGOT_PASSWORD) {
                                    Text("Back to ", color = Color.Gray, fontSize = 14.sp)
                                    Text("Login", color = AppTheme.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { authMode = AuthMode.SIGN_IN })
                                } else {
                                    Text(if (mode == AuthMode.SIGN_UP) "Already have an account? " else "Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                                    Text(if (mode == AuthMode.SIGN_UP) "Sign In" else "Sign Up", color = AppTheme.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun AuthScreenPreview() { MaterialTheme { AuthScreen(onSignIn = { _, _ -> }, onSignUp = { _, _, _, _ -> }, onResetPassword = {}) } }