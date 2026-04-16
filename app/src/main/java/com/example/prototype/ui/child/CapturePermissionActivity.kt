package com.example.prototype.ui.child

// --- ANDROID & CORE ---
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

// --- JETPACK COMPOSE & ACTIVITY SUPPORT ---
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

// --- SERVICE ---
import com.example.prototype.service.ScreenCaptureService

/**
 * CapturePermissionActivity:
 * A transparent bridge activity used to launch the system's screen capture permission dialog.
 * This is necessary because the MediaProjection permission request must be launched
 * from an Activity context, even if the result is intended for a background Service.
 */
class CapturePermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                // 1. Start the Service with permission data
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
            }
            // 3. Close this bridge activity
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}