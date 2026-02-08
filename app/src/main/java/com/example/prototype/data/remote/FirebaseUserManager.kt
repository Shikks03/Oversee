package com.example.prototype.data.remote

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
            .update(field, value)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Ensure generated device IDs are unique in the cloud
    // Might be unoptimized
    fun generateUniqueDeviceId(onResult: (String) -> Unit) {
        val newId = (100000..999999).random().toString()
        db.collection("users").whereEqualTo("device_id", newId).get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) onResult(newId)
                else generateUniqueDeviceId(onResult)
            }
    }
}