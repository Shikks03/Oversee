package com.example.oversee.data

import android.content.Context
import com.example.oversee.data.remote.FcmTokenManager
import com.example.oversee.data.remote.FirebaseAuthManager
import com.example.oversee.data.remote.FirebaseUserManager
import com.example.oversee.data.remote.LoginRateLimiter

object AuthRepository {

    fun isUserLoggedIn(): Boolean = FirebaseAuthManager.isLoggedIn()
    fun getUserId(): String? = FirebaseAuthManager.getUid()

    fun signIn(context: Context, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        LoginRateLimiter.checkAndProceed(
            onBlocked = { errorMsg -> onResult(false, errorMsg) },
            onReady = { ip ->
                FirebaseAuthManager.signIn(email, pass) { success, error ->
                    if (success) {
                        LoginRateLimiter.resetAttempts(ip)
                        val uid = getUserId() ?: return@signIn
                        UserRepository.refreshLocalProfile(context, uid) {
                            context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                                .edit().remove("pending_fcm_token").apply()
                            FcmTokenManager.refreshAndStoreToken(uid)
                            onResult(true, null)
                        }
                    } else {
                        LoginRateLimiter.recordFailedAttempt(ip) { attemptsLeft, lockoutMsg ->
                            val msg = lockoutMsg
                                ?: "Incorrect credentials. You have $attemptsLeft attempt(s) left."
                            onResult(false, msg)
                        }
                    }
                }
            }
        )
    }

    fun register(context: Context, name: String, email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        FirebaseAuthManager.signUp(email, pass) { success, error ->
            if (success) {
                val uid = getUserId() ?: return@signUp
                val profile = mapOf(
                    "name" to name,
                    "email" to email,
                    "created_at" to System.currentTimeMillis()
                )
                FirebaseUserManager.createUserProfile(uid, profile) {
                    UserRepository.refreshLocalProfile(context, uid) {
                        FcmTokenManager.refreshAndStoreToken(uid)
                        onResult(true, null)
                    }
                }
            } else onResult(false, error)
        }
    }

    fun logout(context: Context) {
        getUserId()?.let { uid -> FcmTokenManager.clearToken(uid) }
        FirebaseAuthManager.signOut()
        UserRepository.clearLocalData(context)
    }
}
