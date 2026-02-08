package com.example.prototype.data.remote

import com.google.firebase.auth.FirebaseAuth

/**
 * Technical implementation of Firebase Authentication tasks.
 * ONLY this file (and other remote managers) should import Firebase libraries.
 */
object FirebaseAuthManager {
    private val auth = FirebaseAuth.getInstance()

    // Technical Sign In
    fun signIn(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }

    // Technical Sign Up
    fun signUp(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }

    fun getUid(): String? = auth.currentUser?.uid
    fun signOut() = auth.signOut()
    fun isLoggedIn(): Boolean = auth.currentUser != null
}