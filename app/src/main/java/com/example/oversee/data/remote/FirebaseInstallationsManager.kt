package com.example.oversee.data.remote

import android.util.Log
import com.google.firebase.installations.FirebaseInstallations

object FirebaseInstallationsManager {

    private const val TAG = "FirebaseInstallationsManager"

    fun getId(onResult: (String?) -> Unit) {
        FirebaseInstallations.getInstance().id
            .addOnSuccessListener { fid -> onResult(fid) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to retrieve Firebase Installation ID", e)
                onResult(null)
            }
    }
}
