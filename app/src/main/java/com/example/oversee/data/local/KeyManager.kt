package com.example.oversee.data.local

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

    // In-memory cache keyed by deviceId — avoids confusing parent and child keys
    private val keyCache = mutableMapOf<String, SecretKey>()

    // Single-key cache used only by the child device (getOrCreateKey)
    private var cachedKey: SecretKey? = null

    /**
     * Child-device path: generates a new key if none exists anywhere.
     * DO NOT call this on the parent device.
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

    /**
     * Parent-device path: fetches the child's key from Firestore only.
     * Returns null if the key is not found — never generates a new key.
     * Uses per-deviceId caching so parent and child keys never collide.
     */
    fun getKeyForDevice(context: Context, deviceId: String, onReady: (SecretKey?) -> Unit) {
        keyCache[deviceId]?.let { onReady(it); return }

        val localKey = loadLocalKeyForDevice(context, deviceId)
        if (localKey != null) {
            keyCache[deviceId] = localKey
            onReady(localKey)
            return
        }

        fetchKeyFromFirestore(deviceId) { remoteKey ->
            if (remoteKey != null) {
                storeLocalKeyForDevice(context, deviceId, remoteKey)
                keyCache[deviceId] = remoteKey
            } else {
                Log.w(TAG, "No encryption key found in Firestore for deviceId=$deviceId")
            }
            onReady(remoteKey)
        }
    }

    private fun loadLocalKeyForDevice(context: Context, deviceId: String): SecretKey? {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("key_$deviceId", null) ?: return null
        return try {
            CryptoManager.keyFromBytes(Base64.decode(encoded, Base64.NO_WRAP))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local key for deviceId=$deviceId", e)
            null
        }
    }

    private fun storeLocalKeyForDevice(context: Context, deviceId: String, key: SecretKey) {
        val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("key_$deviceId", encoded)
            .apply()
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

    /** Clears all in-memory cached keys (call on logout). */
    fun clearCache() {
        cachedKey = null
        keyCache.clear()
    }
}
