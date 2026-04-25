package com.example.oversee.data

import android.content.Context
import android.util.Log
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseInstallationsManager
import com.example.oversee.data.remote.FirebaseUserManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object DeviceRepository {

    private const val TAG = "DeviceRepository"
    private val db = FirebaseFirestore.getInstance()

    fun getFid(onResult: (String?) -> Unit) {
        FirebaseInstallationsManager.getId(onResult)
    }

    /**
     * Deterministic 6-digit display code derived from a FID. UI-only label —
     * the FID remains the canonical identifier in Firestore and encryption.
     * Same FID always maps to the same code; collisions are visual noise only.
     */
    fun toDisplayCode(fid: String?): String {
        if (fid.isNullOrBlank()) return ""
        return "%06d".format(Math.floorMod(fid.hashCode(), 1_000_000))
    }

    fun getOrCreateDisplayUid(uid: String, onComplete: (String) -> Unit) {
        FirebaseUserManager.fetchProfile(uid) { profile ->
            val existing = profile?.get("child_display_uid") as? String
            if (!existing.isNullOrBlank()) {
                onComplete(existing)
                return@fetchProfile
            }
            tryReserveDisplayUid(uid, onComplete)
        }
    }

    private fun tryReserveDisplayUid(uid: String, onComplete: (String) -> Unit) {
        val candidate = "%06d".format((100_000..999_999).random())
        FirebaseUserManager.reserveDisplayUid(uid, candidate) { success ->
            if (success) onComplete(candidate) else tryReserveDisplayUid(uid, onComplete)
        }
    }

    fun fetchDeviceDoc(uid: String, fid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .get()
            .addOnSuccessListener { onResult(it.data) }
            .addOnFailureListener { e ->
                Log.e(TAG, "fetchDeviceDoc failed: uid=$uid fid=$fid", e)
                onResult(null)
            }
    }

    fun writeDeviceDoc(uid: String, fid: String, data: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "writeDeviceDoc failed: uid=$uid fid=$fid", e)
                onComplete(false)
            }
    }

    fun setRoleForThisDevice(
        context: Context,
        uid: String,
        fid: String,
        role: String,
        onComplete: (Boolean) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val deviceData = mapOf(
            "role" to role,
            "created_at" to now,
            "last_seen" to now
        )

        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener {
                // Mirror the FID pointer on the family doc so either side can look up the other.
                val fidField = if (role == "PARENT") "parent_device_fid" else "child_device_fid"
                db.collection("users").document(uid)
                    .set(mapOf(fidField to fid), SetOptions.merge())
                    .addOnSuccessListener {
                        AppPreferenceManager.saveString(context, "role", role)
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "setRoleForThisDevice: failed to mirror FID on family doc uid=$uid role=$role", e)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "setRoleForThisDevice: failed to write device doc uid=$uid fid=$fid", e)
                onComplete(false)
            }
    }

    fun refreshFcmToken(uid: String, fid: String, token: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .set(mapOf("fcm_token" to token), SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "refreshFcmToken failed: uid=$uid fid=$fid", e)
                onComplete(false)
            }
    }

    fun clearToken(uid: String, fid: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .update(mapOf("fcm_token" to FieldValue.delete()))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "clearToken failed: uid=$uid fid=$fid", e)
                onComplete(false)
            }
    }
}
