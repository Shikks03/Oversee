package com.example.prototype.data

import android.content.Context
import com.example.prototype.data.remote.FirebaseAuthManager
import com.example.prototype.data.remote.FirebaseUserManager

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
                UserRepository.refreshLocalProfile(context, uid) { onResult(true, null) }
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
                        UserRepository.refreshLocalProfile(context, uid) { onResult(true, null) }
                    }
                }
            } else onResult(false, error)
        }
    }

    fun logout(context: Context) {
        FirebaseAuthManager.signOut()
        UserRepository.clearLocalData(context) // Ensure clearLocalData exists in UserRepository
    }
}