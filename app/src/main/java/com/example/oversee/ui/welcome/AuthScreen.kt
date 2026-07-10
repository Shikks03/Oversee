// oversee/ui/welcome/AuthScreen.kt

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked

import com.example.oversee.ui.components.inputs.OverSeeTextField
import com.example.oversee.ui.theme.AppTheme

enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String) -> Unit,
    isLoading: Boolean = false
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // NEW: State to hold password policy errors
    var passwordError by remember { mutableStateOf<String?>(null) }

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
                Icon(
                    modifier = Modifier.size(100.dp),
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "App Icon",
                    tint = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "OverSee",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
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
                            text = if (mode == AuthMode.SIGN_IN) "Welcome Back" else "Sign Up",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left
                        )
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

                    Spacer(modifier = Modifier.height(16.dp))

                    OverSeeTextField(
                        // Clear error automatically when the user starts typing again
                        value = password, onValueChange = { password = it; passwordError = null },
                        label = "Password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = passwordError != null, // Triggers red outline
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 8.dp)
                        ) {
                            Text(
                                text = "Password Requirements:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )

                            val hasMinLength = password.length >= 8
                            val hasUppercase = password.any { it.isUpperCase() }
                            val hasLowercase = password.any { it.isLowerCase() }
                            val hasNumber = password.any { it.isDigit() }
                            val hasSpecial = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }

                            RequirementRow("At least 8 characters", hasMinLength)
                            RequirementRow("One uppercase letter", hasUppercase)
                            RequirementRow("One lowercase letter", hasLowercase)
                            RequirementRow("One number", hasNumber)
                            RequirementRow("One special character", hasSpecial)
                        }
                    }
                    // NEW: Display the password policy error message
                    AnimatedVisibility(visible = passwordError != null) {
                        Text(
                            text = passwordError ?: "",
                            color = AppTheme.Error,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp)
                        )
                    }

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

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (!isLoading) {
                                if (authMode == AuthMode.SIGN_IN) {
                                    onSignIn(email, password)
                                } else {
                                    // NEW: Password Complexity Regex Check
                                    val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=!]).{8,}\$"
                                    if (!password.matches(passwordPattern.toRegex())) {
                                        passwordError = "Password must be at least 8 characters long, contain an uppercase letter, a number, and a special character."
                                        return@Button
                                    }

                                    passwordError = null
                                    onSignUp(name, email, password, confirmPassword)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (authMode == AuthMode.SIGN_IN) "Signing in..." else "Creating account...",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        } else {
                            Text(
                                text = if (authMode == AuthMode.SIGN_IN) "Sign In" else "Create Account",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .clickable {
                                // Clear inputs and errors when switching modes
                                authMode = if (authMode == AuthMode.SIGN_UP) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                                passwordError = null
                                password = ""
                                confirmPassword = ""
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (authMode == AuthMode.SIGN_UP) "Already have an account? " else "Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                        Text(if (authMode == AuthMode.SIGN_UP) "Sign In" else "Sign Up", color = AppTheme.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RequirementRow(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isMet) Color(0xFF4CAF50) else Color.LightGray, // Green if met
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isMet) Color.DarkGray else Color.Gray
        )
    }
}