package com.example.prototype.data.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import javax.crypto.SecretKey

/**
 * Manages the ChaCha20 encryption key lifecycle:
 * - Child device: generates key, stores locally, uploads to Firestore
 * - Parent device: fetches key from Firestore using child's device_id, stores locally
 *
 * Key is stored at: monitor_sessions/{device_id}/config (document), field "encryption_key"
 */
object KeyManager {

    private const val TAG = "KeyManager"
    private const val PREFS_NAME = "CryptoPrefs"
    private const val KEY_LOCAL = "encryption_key"
    private const val COLLECTION_SESSIONS = "monitor_sessions"
    private const val FIELD_KEY = "encryption_key"

    // In-memory cache to avoid repeated Firestore reads within a session
    private var cachedKey: SecretKey? = null

    /**
     * Returns the encryption key for the given device_id.
     * Checks memory → local SharedPreferences → Firestore.
     * If not found anywhere, generates a new key and persists it.
     */
    fun getOrCreateKey(context: Context, deviceId: String, onReady: (SecretKey) -> Unit) {
        cachedKey?.let { onReady(it); return }

        val localKey = loadLocalKey(context)
        if (localKey != null) {
            cachedKey = localKey
            onReady(localKey)
            return
        }

        fetchKeyFromFirestore(deviceId) { remoteKey ->
            if (remoteKey != null) {
                storeKeyLocally(context, remoteKey)
                cachedKey = remoteKey
                onReady(remoteKey)
            } else {
                // No key exists yet — generate one (child device first run)
                val newKey = CryptoManager.generateKey()
                storeKeyLocally(context, newKey)
                cachedKey = newKey
                uploadKeyToFirestore(deviceId, newKey) { success ->
                    if (!success) Log.e(TAG, "Failed to upload encryption key to Firestore")
                }
                onReady(newKey)
            }
        }
    }

    fun storeKeyLocally(context: Context, key: SecretKey) {
        val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCAL, encoded)
            .apply()
    }

    fun loadLocalKey(context: Context): SecretKey? {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCAL, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            CryptoManager.keyFromBytes(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local key", e)
            null
        }
    }

    fun uploadKeyToFirestore(deviceId: String, key: SecretKey, onComplete: (Boolean) -> Unit = {}) {
        val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        FirebaseFirestore.getInstance()
            .collection(COLLECTION_SESSIONS)
            .document(deviceId)
            .set(mapOf(FIELD_KEY to encoded), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload key", e)
                onComplete(false)
            }
    }

    fun fetchKeyFromFirestore(deviceId: String, onResult: (SecretKey?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection(COLLECTION_SESSIONS)
            .document(deviceId)
            .get()
            .addOnSuccessListener { doc ->
                val encoded = doc.getString(FIELD_KEY)
                if (encoded != null) {
                    try {
                        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                        onResult(CryptoManager.keyFromBytes(bytes))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode remote key", e)
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch key from Firestore", e)
                onResult(null)
            }
    }

    /** Clears cached key from memory (call on logout). */
    fun clearCache() {
        cachedKey = null
    }
}
