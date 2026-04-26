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

import com.example.oversee.ui.components.inputs.OverSeeTextField
import com.example.oversee.ui.theme.AppTheme

enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String) -> Unit
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

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (authMode == AuthMode.SIGN_IN) onSignIn(email, password)
                            else onSignUp(name, email, password, confirmPassword)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
                    ) {
                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) "Sign In" else "Create Account",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
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
                        Text(if (authMode == AuthMode.SIGN_UP) "Already have an account? " else "Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                        Text(if (authMode == AuthMode.SIGN_UP) "Sign In" else "Sign Up", color = AppTheme.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}