package com.example.prototype.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.example.prototype.data.AuthRepository
import com.example.prototype.data.UserRepository
import com.example.prototype.ui.theme.AppTheme


// In SignInActivity.kt
class SignInActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 1. AUTO-LOGIN CHECK: If Firebase user exists, skip login
        if (AuthRepository.isUserLoggedIn()) {
            val uid = AuthRepository.getUserId()
            if (uid != null) {
                // Ensure local data is fresh before entering
                UserRepository.refreshLocalProfile(this, uid) {
                    navigateToRoleSelection()
                }
            }
            return
        }

        setContent {
            MaterialTheme {
                SignInScreen(
                    onSignIn = { email, pass -> performSignIn(email, pass) },
                    onSignUpClick = { startActivity(Intent(this, SignUpActivity::class.java)) }
                )
            }
        }
    }

    private fun performSignIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        AuthRepository.signIn(this, email, pass) { success, error ->
            if (success) {
                navigateToRoleSelection()
            } else {
                Toast.makeText(this, "Sign In Failed: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToRoleSelection() {
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }

//    /**
//     * 🟢 SLAPPED FUNCTION: Fetches the IDs from Firestore so they are
//     * remembered immediately after login.
//     */
//    private fun syncCloudDataAndNavigate() {
//        val uid = auth.currentUser?.uid ?: return
//
//        db.collection("users").document(uid).get()
//            .addOnSuccessListener { document ->
//                if (document.exists()) {
//                    val prefs = getSharedPreferences("AppConfig", MODE_PRIVATE)
//
//                    val cloudChildId = document.getString("device_id") ?: ""
//                    val cloudLinkedId = document.getString("linked_child_id") ?: ""
//
//                    prefs.edit {
//                        // Restore Child Device ID if it exists in cloud
//                        if (cloudChildId.isNotEmpty()) putString("device_id", cloudChildId)
//                        // Restore Parent's Link if it exists in cloud
//                        if (cloudLinkedId.isNotEmpty()) putString("target_id", cloudLinkedId)
//                    }
//                }
//                navigateToRoleSelection()
//            }
//            .addOnFailureListener {
//                // Even if sync fails, proceed to role selection
//                navigateToRoleSelection()
//            }
//    }
}

@Composable
fun SignInScreen(onSignIn: (String, String) -> Unit, onSignUpClick: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(AppTheme.SecBackground), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Icon(
                modifier = Modifier.size(150.dp),
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "OverSee Icon",
                tint = AppTheme.Surface
            )
            Text("Welcome!", Modifier.padding(36.dp), style = AppTheme.TitlePageStyle, color = AppTheme.Surface, textAlign = TextAlign.Center)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(44.dp, 44.dp, 0.dp, 0.dp)
            ) {
                Column(Modifier.padding(57.dp, 71.dp, 57.dp, 100.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top)) {
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

                    Button(
                        onClick = { onSignIn(email, password) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.Primary)
                    ) {
                        Text("Login", style = AppTheme.BodyBase, color = AppTheme.Surface)
                    }
                    TextButton(onClick = onSignUpClick) {
                        Text("Don't have an account? Sign Up", style = AppTheme.BodyBase, textDecoration = TextDecoration.Underline)
                    }
                }
            }
        }

    }
}
// --- PREVIEWS ---

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SignInScreenPreview() {
    MaterialTheme {
        SignInScreen({ _, _ -> }, {})
    }
}