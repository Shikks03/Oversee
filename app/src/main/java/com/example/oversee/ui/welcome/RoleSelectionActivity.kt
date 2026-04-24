package com.example.oversee.ui.welcome

// --- ANDROID & CORE ---
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// --- JETPACK COMPOSE UI ---
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*

// --- COMPOSE MATERIAL & ICONS ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

// --- COMPOSE RUNTIME ---
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.oversee.data.AuthRepository
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.UserRepository

// --- PROJECT SPECIFIC ---
import com.example.oversee.ui.child.ChildDashboardActivity
import com.example.oversee.ui.parent.ParentDashboardActivity
import com.example.oversee.ui.theme.AppTheme
import com.example.oversee.ui.theme.Responsive


/**
 * RoleSelectionActivity
 * * LOGIC FLOW SUMMARY:
 * 1. App Launch -> onCreate().
 * 2. Check Persistence -> attemptAutoLogin() checks if a role exists in SharedPreferences.
 * 3. Decision Point:
 * - IF ROLE EXISTS: Direct navigation to specific Dashboard (Flow ends here).
 * - IF NO ROLE: Render RoleSelectionScreen UI.
 * 4. User Interaction:
 * - Child Selected -> handleChildLogin() -> Generate ID -> Save Role -> Dashboard.
 * - Parent Selected -> handleParentLogin() -> Save Role -> Dashboard.
 */
class RoleSelectionActivity : ComponentActivity() {

    // --- LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /**
         * SEQUENCE STEP 1: Auto-Login Check.
         * We do this BEFORE setContent to prevent the UI from "flashing" the selection
         * screen if the user is already logged in.
         */
        if (attemptAutoLogin()) return

        val userName = UserRepository.getLocalName(this)

        /**
         * SEQUENCE STEP 2: Render UI.
         * Only reached if attemptAutoLogin returns false (no session found).
         */
        setContent {
            MaterialTheme {
                RoleSelectionScreen(
                    user = userName,
                    onSelectChild = { selectRole("CHILD") },
                    onSelectParent = { selectRole("PARENT") }
                )
            }
        }
    }

    // --- LOGIC: SESSION MANAGEMENT ---

    /**
     * Checks if a role is already saved in local storage (AppConfig).
     */
    private fun attemptAutoLogin(): Boolean {
        val currentRole = UserRepository.getLocalRole(this)
        return when (currentRole) {
            "CHILD" -> {
                navigateToDashboard(ChildDashboardActivity::class.java)
                true
            }
            "PARENT" -> {
                navigateToDashboard(ParentDashboardActivity::class.java)
                true
            }
            else -> false // role is "NOT_SET"
        }
    }

    /**
     * Gets this phone's FID, writes the device doc with the chosen role, mirrors
     * the FID pointer on the family doc, then navigates to the appropriate dashboard.
     */
    private fun selectRole(role: String) {
        val uid = AuthRepository.getUserId() ?: return
        DeviceRepository.getFid { fid ->
            if (fid == null) {
                Toast.makeText(this, "Could not identify this device. Try again.", Toast.LENGTH_SHORT).show()
                return@getFid
            }
            DeviceRepository.setRoleForThisDevice(this, uid, fid, role) { success ->
                if (success) {
                    val target = if (role == "CHILD") ChildDashboardActivity::class.java
                    else ParentDashboardActivity::class.java
                    navigateToDashboard(target)
                } else {
                    Toast.makeText(this, "Failed to set up this device. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * NAVIGATION SEQUENCE:
     * 1. Starts the target activity (Parent or Child Dashboard).
     * 2. Calls finish().
     * * STRATEGIC NOTE: By calling finish(), we remove RoleSelectionActivity from the
     * Android Back Stack. This means if the user presses "Back" from the Dashboard,
     * they exit the app instead of coming back to this selection screen.
     */
    private fun navigateToDashboard(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}

