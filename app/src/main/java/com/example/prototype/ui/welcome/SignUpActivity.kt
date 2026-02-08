package com.example.prototype.ui.welcome

// --- IMPORTS ---

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.prototype.ui.theme.AppTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.example.prototype.data.AuthRepository


class SignUpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SignUpScreen(
                    onSignUp = { name, email, password ->
                        performRegistration(name, email, password)
                    },
                    onLoginClick = {
                        startActivity(Intent(this, SignInActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun performRegistration(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        AuthRepository.register(this, name, email, pass) { success, error ->
            if (success) {
                Toast.makeText(this, "Account Created!", Toast.LENGTH_SHORT).show()
                // Navigate to Role Selection so they can choose Parent/Child
                startActivity(Intent(this, RoleSelectionActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Registration Failed: $error", Toast.LENGTH_LONG).show()
            }
        }
    }
}


@Composable
fun SignUpScreen(onSignUp: (String, String, String) -> Unit, onLoginClick: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 1. Main Background Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.SecBackground), // Matches Login Blue
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // 2. Logo & Header (Matches Login)
            Icon(
                modifier = Modifier.size(150.dp).padding(30.dp),
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "OverSee Icon",
                tint = AppTheme.Surface
            )



            // 3. Bottom Sheet Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppTheme.Surface),
                shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
            ) {
                Column(
                    // Reduced top padding slightly (71dp -> 40dp) to fit the extra field comfortably
                    modifier = Modifier.padding(start = 57.dp, top = 64.dp, end = 57.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    Text(
                        text = "Sign Up",
                        modifier = Modifier.fillMaxWidth(),
                        style = AppTheme.TitlePageStyle,
                        textAlign = TextAlign.Left
                    )

                    // -- FIELDS --
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name", style = AppTheme.BodyBase, color = AppTheme.TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", style = AppTheme.BodyBase, color = AppTheme.TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", style = AppTheme.BodyBase, color = AppTheme.TextTertiary) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // -- BUTTONS --
                    Column(horizontalAlignment = Alignment.CenterHorizontally){
                        Button(
                            onClick = { onSignUp(name, email, password) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
                        ) {
                            Text("Create Account", style = AppTheme.BodyBase, color = AppTheme.Surface)
                        }

                        TextButton(onClick = onLoginClick) {
                            Text(
                                text = "Already have an account? Login",
                                style = AppTheme.BodyBase,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }


                }
            }
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_6")
@Composable
fun SignUpScreenPreview() {
    MaterialTheme {
        SignUpScreen(
            onSignUp = { _, _, _ -> },
            onLoginClick = {}
        )
    }
}