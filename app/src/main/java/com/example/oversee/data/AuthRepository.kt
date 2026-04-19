package com.example.oversee.data

import android.content.Context
import com.example.oversee.data.remote.FcmTokenManager
import com.example.oversee.data.remote.FirebaseAuthManager
import com.example.oversee.data.remote.FirebaseUserManager

/**
 * Manages user authentication state and coordinates with the data layer.
 */
object AuthRepository {

    fun isUserLoggedIn(): Boolean = FirebaseAuthManager.isLoggedIn()
    fun getUserId(): String? = FirebaseAuthManager.getUid()

    fun signIn(context: Context, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        FirebaseAuthManager.signIn(email, pass) { success, error ->
            if (success) {
                val uid = getUserId() ?: return@signIn
                UserRepository.refreshLocalProfile(context, uid) {
                    // #6 — Upload any token that rotated while the user was logged out
                    val prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                    val pendingToken = prefs.getString("pending_fcm_token", null)
                    if (pendingToken != null) {
                        com.example.oversee.data.remote.FirebaseUserManager
                            .updateProfileField(uid, "fcm_token", pendingToken) { _ ->
                                prefs.edit().remove("pending_fcm_token").apply()
                            }
                    } else {
                        FcmTokenManager.refreshAndStoreToken(uid)
                    }
                    onResult(true, null)
                }
            } else {
                onResult(false, error)
            }
        }
    }

    fun register(context: Context, name: String, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        FirebaseAuthManager.signUp(email, pass) { success, error ->
            if (success) {
                val uid = getUserId() ?: return@signUp
                FirebaseUserManager.generateUniqueDeviceId { newId ->
                    val profile = mapOf(
                        "name" to name,
                        "email" to email,
                        "role" to "NOT_SET",
                        "device_id" to newId,
                        "linked_child_id" to "NOT_LINKED",
                        "created_at" to System.currentTimeMillis()
                    )
                    FirebaseUserManager.createUserProfile(uid, profile) {
                        UserRepository.refreshLocalProfile(context, uid) {
                            FcmTokenManager.refreshAndStoreToken(uid)
                            onResult(true, null)
                        }
                    }
                }
            } else onResult(false, error)
        }
    }

    fun logout(context: Context) {
        getUserId()?.let { uid -> FcmTokenManager.clearToken(uid) }
        FirebaseAuthManager.signOut()
        UserRepository.clearLocalData(context) // Ensure clearLocalData exists in UserRepository
    }
}