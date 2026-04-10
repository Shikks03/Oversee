package com.example.prototype.data.remote

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manages FCM token lifecycle: retrieval, Firestore storage, and cleanup on logout.
 * Token is stored at users/{uid}/fcm_token so the Cloud Function can look it up.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val FIELD_FCM_TOKEN = "fcm_token"

    /**
     * Retrieves the current FCM token and stores it in Firestore under users/{uid}.
     * Should be called after sign-in and registration.
     */
    fun refreshAndStoreToken(uid: String, onComplete: (Boolean) -> Unit = {}) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token retrieved, uploading to Firestore")
                FirebaseUserManager.updateProfileField(uid, FIELD_FCM_TOKEN, token) { success ->
                    if (!success) Log.w(TAG, "Failed to store FCM token in Firestore")
                    onComplete(success)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to retrieve FCM token", e)
                onComplete(false)
            }
    }

    /**
     * Removes the FCM token from Firestore on logout so stale tokens aren't used.
     */
    fun clearToken(uid: String) {
        FirebaseUserManager.updateProfileField(uid, FIELD_FCM_TOKEN, "") { success ->
            if (!success) Log.w(TAG, "Failed to clear FCM token from Firestore")
        }
    }
}
