package com.example.oversee.data.remote

import android.util.Log
import com.example.oversee.data.DeviceRepository
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    fun refreshAndStoreToken(uid: String, onComplete: (Boolean) -> Unit = {}) {
        DeviceRepository.getFid { fid ->
            if (fid == null) {
                Log.w(TAG, "Could not get FID to store FCM token")
                onComplete(false)
                return@getFid
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    DeviceRepository.refreshFcmToken(uid, fid, token, onComplete)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to retrieve FCM token", e)
                    onComplete(false)
                }
        }
    }

    fun clearToken(uid: String) {
        DeviceRepository.getFid { fid ->
            if (fid == null) {
                Log.w(TAG, "Could not get FID to clear FCM token")
                return@getFid
            }
            DeviceRepository.clearToken(uid, fid) { success ->
                if (!success) Log.w(TAG, "Failed to clear FCM token from Firestore")
            }
        }
    }
}
