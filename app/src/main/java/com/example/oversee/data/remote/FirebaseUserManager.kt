package com.example.oversee.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Technical implementation of Firestore operations for User data.
 */
object FirebaseUserManager {
    private val db = FirebaseFirestore.getInstance()

    // Technical Task: Create or Overwrite document
    fun createUserProfile(uid: String, profile: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid).set(profile)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Technical Task: Fetch document
    fun fetchProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.data) }
            .addOnFailureListener { onResult(null) }
    }

    fun updateProfileField(uid: String, field: String, value: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .set(mapOf(field to value), SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e("FirebaseUserManager", "updateProfileField failed: uid=$uid field=$field", e)
                onComplete(false)
            }
    }

    fun deleteProfileField(uid: String, field: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .update(mapOf(field to FieldValue.delete()))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e("FirebaseUserManager", "deleteProfileField failed: uid=$uid field=$field", e)
                onComplete(false)
            }
    }

    // Generates a 9-digit ID derived from a UUID (128-bit random).
    // No Firestore query needed — collision probability is negligible.
    fun generateUniqueDeviceId(onResult: (String) -> Unit) {
        val uuid = java.util.UUID.randomUUID()
        val bits = (uuid.mostSignificantBits xor uuid.leastSignificantBits) and Long.MAX_VALUE
        val id = (100_000_000L + (bits % 900_000_000L)).toString()
        onResult(id)
    }
}