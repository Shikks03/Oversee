package com.example.oversee.ui.child

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme

import com.example.oversee.data.AuthRepository
import com.example.oversee.data.UserRepository
import com.example.oversee.ui.welcome.RoleSelectionActivity
import com.example.oversee.ui.welcome.SignInActivity

class ChildDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContent {
            MaterialTheme {
                ChildDashboardRoute(
                    onLogoutClick = { performLogout() },
                    onDebugResetRole = { debugResetRole() }
                )
            }
        }
    }

    private fun performLogout() {
        UserRepository.clearLocalRole(this)
        AuthRepository.logout(this)
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun debugResetRole() {
        UserRepository.clearLocalRole(this)
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }
}
