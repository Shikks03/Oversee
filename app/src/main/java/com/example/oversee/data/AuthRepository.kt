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

                        // --- FIX: Run heavy crypto math on a background thread ---
                        Thread {
                            val derivedKek = com.example.oversee.data.local.CryptoManager.deriveKeyEncryptionKey(pass, email)

                            // Switch back to Main Thread for UI updates
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                com.example.oversee.data.local.KeyManager.sessionKek = derivedKek

                                // --- NEW: Save the KEK to secure hardware storage so it survives restarts ---
                                val kekBase64 = android.util.Base64.encodeToString(derivedKek.encoded, android.util.Base64.NO_WRAP)
                                com.example.oversee.data.local.AppPreferenceManager.saveString(context, "secure_session_kek", kekBase64)

                                UserRepository.refreshLocalProfile(context, uid) {
                                    context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                                        .edit().remove("pending_fcm_token").apply()
                                    FcmTokenManager.refreshAndStoreToken(uid)
                                    onResult(true, null)
                                }
                            }
                        }.start()
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

                // --- FIX: Derive the KEK during Registration too! ---
                Thread {
                    val derivedKek = com.example.oversee.data.local.CryptoManager.deriveKeyEncryptionKey(pass, email)

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        com.example.oversee.data.local.KeyManager.sessionKek = derivedKek

                        // --- NEW: Save the KEK to secure hardware storage so it survives restarts ---
                        val kekBase64 = android.util.Base64.encodeToString(derivedKek.encoded, android.util.Base64.NO_WRAP)
                        com.example.oversee.data.local.AppPreferenceManager.saveString(context, "secure_session_kek", kekBase64)

                        FirebaseUserManager.createUserProfile(uid, profile) {
                            UserRepository.refreshLocalProfile(context, uid) {
                                FcmTokenManager.refreshAndStoreToken(uid)
                                onResult(true, null)
                            }
                        }
                    }
                }.start()
                // ---------------------------------------------------

            } else onResult(false, error)
        }
    }

    fun logout(context: Context) {
        getUserId()?.let { uid -> FcmTokenManager.clearToken(uid) }
        FirebaseAuthManager.signOut()
        UserRepository.clearLocalData(context)
    }
}
