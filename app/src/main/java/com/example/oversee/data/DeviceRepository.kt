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

    /** One linked child phone, resolved for display. */
    data class ChildDevice(val fid: String, val name: String, val displayUid: String?)

    fun fetchChildDevices(context: Context, uid: String, onResult: (List<ChildDevice>) -> Unit) {
        FirebaseUserManager.fetchProfile(uid) { profile ->
            val legacyFid = profile?.get("child_device_fid") as? String
            val legacyDisplayUid = profile?.get("child_display_uid") as? String
            db.collection("users").document(uid).collection("devices")
                .get()
                .addOnSuccessListener { snapshot ->
                    val childDocs = snapshot.documents
                        .filter { it.getString("role") == "CHILD" }
                        .sortedBy { it.getLong("created_at") ?: 0L }
                    val children = childDocs.mapIndexed { index, doc ->
                        val fid = doc.id
                        val storedName = doc.getString("child_name")
                        val name = when {
                            !storedName.isNullOrBlank() -> storedName
                            fid == legacyFid -> AppPreferenceManager.getString(context, "target_nickname", "Child Device")
                            else -> "Child ${index + 1}"
                        }
                        ChildDevice(fid, name, legacyDisplayUid.takeIf { fid == legacyFid })
                    }.toMutableList()
                    // Heal: legacy pointer exists but device doc is missing.
                    if (legacyFid != null && children.none { it.fid == legacyFid }) {
                        children.add(
                            ChildDevice(
                                legacyFid,
                                AppPreferenceManager.getString(context, "target_nickname", "Child Device"),
                                legacyDisplayUid
                            )
                        )
                        writeDeviceDoc(uid, legacyFid, mapOf("role" to "CHILD", "created_at" to System.currentTimeMillis())) {}
                    }
                    onResult(children)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "fetchChildDevices failed: uid=$uid", e)
                    onResult(emptyList())
                }
        }
    }

    fun renameChild(uid: String, fid: String, name: String, onComplete: (Boolean) -> Unit) {
        writeDeviceDoc(uid, fid, mapOf("child_name" to name), onComplete)
    }

    fun deleteDeviceDoc(uid: String, fid: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(uid)
            .collection("devices").document(fid)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteDeviceDoc failed: uid=$uid fid=$fid", e)
                onComplete(false)
            }
    }

    /** Reserved 6-digit code for the legacy child; deterministic hash for everyone else. */
    fun getDisplayUidForChild(uid: String, fid: String, onComplete: (String) -> Unit) {
        FirebaseUserManager.fetchProfile(uid) { profile ->
            val legacyFid = profile?.get("child_device_fid") as? String
            val legacyDisplayUid = profile?.get("child_display_uid") as? String
            onComplete(
                if (fid == legacyFid && !legacyDisplayUid.isNullOrBlank()) legacyDisplayUid
                else toDisplayCode(fid)
            )
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
                val finish = {
                    AppPreferenceManager.saveString(context, "role", role)
                    if (role == "CHILD") AppPreferenceManager.saveString(context, "parent_id", uid)
                    onComplete(true)
                }
                if (role == "PARENT") {
                    // Mirror the parent FID pointer on the family doc (read by the Cloud Function).
                    db.collection("users").document(uid)
                        .set(mapOf("parent_device_fid" to fid), SetOptions.merge())
                        .addOnSuccessListener { finish() }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "setRoleForThisDevice: failed to mirror FID on family doc uid=$uid", e)
                            onComplete(false)
                        }
                } else {
                    // CHILD devices are tracked solely via their device doc; the legacy
                    // child_device_fid scalar stays frozen as the legacy-child marker.
                    finish()
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
