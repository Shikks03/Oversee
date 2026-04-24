package com.example.oversee.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Technical implementation of Firestore operations for User data.
 */
object FirebaseUserManager {
    private const val TAG = "FirebaseUserManager"
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
                Log.e(TAG, "updateProfileField failed: uid=$uid field=$field", e)
                onComplete(false)
            }
    }

    fun deleteProfileField(uid: String, field: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .update(mapOf(field to FieldValue.delete()))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteProfileField failed: uid=$uid field=$field", e)
                onComplete(false)
            }
    }

    fun fetchDeviceFidPointers(uid: String, onResult: (parentFid: String?, childFid: String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val parentFid = doc.getString("parent_device_fid")
                val childFid = doc.getString("child_device_fid")
                onResult(parentFid, childFid)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "fetchDeviceFidPointers FAILED: uid=$uid error=${e.message}", e)
                onResult(null, null)
            }
    }
}