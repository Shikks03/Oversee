package com.example.oversee.data

import android.content.Context
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.remote.FirebaseUserManager

object UserRepository {

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

    private fun saveProfileToLocal(context: Context, profile: Map<String, Any>) {
        AppPreferenceManager.saveString(context, "name", profile["name"] as? String ?: "")
        AppPreferenceManager.saveString(context, "email", profile["email"] as? String ?: "")
        AppPreferenceManager.saveLong(context, "created_at", profile["created_at"] as? Long ?: 0L)
    }

    fun getLocalName(context: Context): String = AppPreferenceManager.getString(context, "name", "")
    fun getLocalEmail(context: Context): String = AppPreferenceManager.getString(context, "email", "")
    fun getLocalRole(context: Context): String = AppPreferenceManager.getString(context, "role", "NOT_SET")
    fun getLocalCreatedAt(context: Context): Long = AppPreferenceManager.getLong(context, "created_at", 0L)

    fun updateUserName(context: Context, uid: String, newName: String, onResult: (Boolean) -> Unit) {
        FirebaseUserManager.updateProfileField(uid, "name", newName) { success ->
            if (success) AppPreferenceManager.saveString(context, "name", newName)
            onResult(success)
        }
    }

    fun clearLocalRole(context: Context) {
        AppPreferenceManager.saveString(context, "role", "NOT_SET")
    }

    fun clearLocalData(context: Context) {
        AppPreferenceManager.clearAll(context)
    }
}
