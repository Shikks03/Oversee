package com.example.prototype.data

import android.content.Context
import com.example.prototype.data.local.AppPreferenceManager
import com.example.prototype.data.remote.FirebaseUserManager

/**
 * Single source of truth for User data.
 * Manages all parameters: name, email, role, device_id, linked_child_id, created_at.
 */
object UserRepository {
    private const val PREFS_NAME = "AppConfig"

    // --- LOGIC: Refresh Local from Cloud ---

    fun refreshLocalProfile(context: Context, uid: String, onComplete: (Boolean) -> Unit) {
        FirebaseUserManager.fetchProfile(uid) { cloudProfile ->
            if (cloudProfile != null) {
                saveProfileToLocal(context, cloudProfile)
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    /**
     * Startup Logic: Resolves identity by checking Cloud -> Local -> Generation.
     */
    fun initializeDeviceId(context: Context, uid: String, onResult: (String) -> Unit) {
        FirebaseUserManager.fetchProfile(uid) { profile ->
            val cloudId = profile?.get("device_id") as? String

            if (!cloudId.isNullOrEmpty()) {
                // 1. Found in Cloud (Source of Truth)
                AppPreferenceManager.saveString(context, "device_id", cloudId)
                onResult(cloudId)
            } else {
                val localId = getLocalDeviceId(context)
                if (localId != "000000" && localId != "NOT_SET") {
                    // 2. Found in Local only, sync to Cloud
                    updateDeviceId(context, uid, localId) { onResult(localId) }
                } else {
                    // 3. Brand New: Generate and Save
                    FirebaseUserManager.generateUniqueDeviceId { newId ->
                        updateDeviceId(context, uid, newId) { onResult(newId) }
                    }
                }
            }
        }
    }

    private fun saveProfileToLocal(context: Context, profile: Map<String, Any>) {
        AppPreferenceManager.saveString(context, "name", profile["name"] as? String ?: "")
        AppPreferenceManager.saveString(context, "email", profile["email"] as? String ?: "")
        AppPreferenceManager.saveString(context, "role", profile["role"] as? String ?: "NOT_SET")
        AppPreferenceManager.saveString(context, "device_id", profile["device_id"] as? String ?: "000000")
        AppPreferenceManager.saveString(context, "target_id", profile["linked_child_id"] as? String ?: "NOT_LINKED")
        AppPreferenceManager.saveLong(context, "created_at", profile["created_at"] as? Long ?: 0L)
    }

    // --- LOGIC: Getters for UI ---
    fun getLocalName(context: Context): String = AppPreferenceManager.getString(context, "name", "")
    fun getLocalEmail(context: Context): String = AppPreferenceManager.getString(context, "email", "")
    fun getLocalRole(context: Context): String = AppPreferenceManager.getString(context, "role", "NOT_SET")
    fun getLocalDeviceId(context: Context): String = AppPreferenceManager.getString(context, "device_id", "000000")
    fun getLocalTargetId(context: Context): String = AppPreferenceManager.getString(context, "target_id", "NOT_LINKED")
    fun getLocalCreatedAt(context: Context): Long = AppPreferenceManager.getLong(context, "created_at", 0L)

    // --- LOGIC: Setters (Local + Cloud) ---
    /**
     * Updates the Device ID to a specific value.
     * Use this for debugging to set a consistent ID like "123456".
     */
    fun updateUserName(context: Context, uid: String, newName: String, onResult: (Boolean) -> Unit) {
        FirebaseUserManager.updateProfileField(uid, "name", newName) { success ->
            if (success) AppPreferenceManager.saveString(context, "name", newName)
            onResult(success)
        }
    }
    fun updateUserRole(context: Context, uid: String, newRole: String, onResult: (Boolean) -> Unit) {
        FirebaseUserManager.updateProfileField(uid, "role", newRole) { success ->
            if (success) AppPreferenceManager.saveString(context, "role", newRole)
            onResult(success)
        }
    }
    fun linkChildDevice(context: Context, uid: String, childId: String, onComplete: (Boolean) -> Unit) {
        FirebaseUserManager.updateProfileField(uid, "linked_child_id", childId) { success ->
            if (success) AppPreferenceManager.saveString(context, "target_id", childId)
            onComplete(success)
        }
    }
    fun updateDeviceId(context: Context, uid: String, newId: String, onResult: (Boolean) -> Unit) {
        FirebaseUserManager.updateProfileField(uid, "device_id", newId) { success ->
            if (success) AppPreferenceManager.saveString(context, "device_id", newId)
            onResult(success)
        }
    }

    fun clearLocalRole(context: Context) {
        // Child logout often keeps the device_id but removes the role
        AppPreferenceManager.saveString(context, "role", "NOT_SET")
//        FirebaseUserManager.updateProfileField(uid, "role", "NOT_SET")
    }

    fun clearLocalData(context: Context) {
        AppPreferenceManager.clearAll(context)
    }

}