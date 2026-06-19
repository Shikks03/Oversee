package com.example.oversee.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * Parent-controlled, per-child discipline config (timeout + punishment chores) plus the
 * runtime punishment-approval handshake. All fields are PLAINTEXT under
 * monitor_sessions/{childFid} — matching the app convention that only incident *log content*
 * is encrypted. Parent and child share one account uid, so firestore.rules already authorizes
 * both for this doc (no rules change needed).
 *
 * Lifecycle of [STATUS_KEY]: NONE (idle) -> ACTIVE (chores pending on the child) ->
 * PENDING_APPROVAL (child finished chores, awaiting parent) -> CLEARED (parent approved).
 */
object PunishmentRepository {

    private const val TAG = "PunishmentRepo"
    private const val COLLECTION_SESSIONS = "monitor_sessions"
    private val db = FirebaseFirestore.getInstance()

    // --- Field names on monitor_sessions/{fid} ---
    const val FIELD_TIMEOUT_ENABLED = "timeout_enabled"
    const val FIELD_BLOCK_DURATION = "block_duration_mins"
    const val FIELD_BURST_THRESHOLD = "burst_threshold"
    const val FIELD_PUNISHMENT_ENABLED = "punishment_enabled"
    const val FIELD_PUNISHMENT_CHORES = "punishment_chores"
    const val FIELD_CONFIG_UPDATED_AT = "config_updated_at"
    const val FIELD_PUNISHMENT_STATUS = "punishment_status"
    const val FIELD_PUNISHMENT_STARTED_AT = "punishment_started_at"

    // --- Punishment status values ---
    const val STATUS_NONE = "NONE"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
    const val STATUS_CLEARED = "CLEARED"

    /** Defaults seeded into a new child's chore list (parent can edit/remove/add). */
    val DEFAULT_CHORES = listOf("Wash the dishes", "Make your bed", "Read for 20 minutes")

    /** Parent-set discipline config for one child. */
    data class Config(
        val timeoutEnabled: Boolean = false,
        val blockDurationMins: Long = 5L,
        val burstThreshold: Long = 55L,
        val punishmentEnabled: Boolean = false,
        val chores: List<String> = DEFAULT_CHORES
    )

    /** One-shot read of a child's config; falls back to sane defaults when fields are absent. */
    fun fetchConfig(childFid: String, onResult: (Config) -> Unit) {
        db.collection(COLLECTION_SESSIONS).document(childFid)
            .get()
            .addOnSuccessListener { snap ->
                if (snap == null || !snap.exists()) {
                    onResult(Config())
                    return@addOnSuccessListener
                }
                @Suppress("UNCHECKED_CAST")
                val chores = (snap.get(FIELD_PUNISHMENT_CHORES) as? List<String>)
                    ?.takeIf { it.isNotEmpty() } ?: DEFAULT_CHORES
                onResult(
                    Config(
                        timeoutEnabled = snap.getBoolean(FIELD_TIMEOUT_ENABLED) ?: false,
                        blockDurationMins = snap.getLong(FIELD_BLOCK_DURATION) ?: 5L,
                        burstThreshold = snap.getLong(FIELD_BURST_THRESHOLD) ?: 55L,
                        punishmentEnabled = snap.getBoolean(FIELD_PUNISHMENT_ENABLED) ?: false,
                        chores = chores
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "fetchConfig failed for $childFid: ${e.message}")
                onResult(Config())
            }
    }

    /** Parent writes the full config and bumps [FIELD_CONFIG_UPDATED_AT] so the child re-syncs. */
    fun saveConfig(childFid: String, config: Config, onComplete: (Boolean) -> Unit = {}) {
        val data = hashMapOf(
            FIELD_TIMEOUT_ENABLED to config.timeoutEnabled,
            FIELD_BLOCK_DURATION to config.blockDurationMins,
            FIELD_BURST_THRESHOLD to config.burstThreshold,
            FIELD_PUNISHMENT_ENABLED to config.punishmentEnabled,
            FIELD_PUNISHMENT_CHORES to config.chores,
            FIELD_CONFIG_UPDATED_AT to System.currentTimeMillis()
        )
        db.collection(COLLECTION_SESSIONS).document(childFid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.w(TAG, "saveConfig failed for $childFid: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * Real-time listener on a child's session doc. Returns the [ListenerRegistration] so callers
     * remove it when done. Used by the parent screen (watch [FIELD_PUNISHMENT_STATUS]) and by the
     * child (cache config + react to parent approval).
     */
    fun listen(childFid: String, onChange: (com.google.firebase.firestore.DocumentSnapshot) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION_SESSIONS).document(childFid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                onChange(snap)
            }
    }

    /** Child: a punishment is now in effect; chores pending. */
    fun setActive(childFid: String) {
        setStatus(
            childFid,
            STATUS_ACTIVE,
            extra = mapOf(FIELD_PUNISHMENT_STARTED_AT to System.currentTimeMillis())
        )
    }

    /** Child: finished all chores, asking the parent to approve. */
    fun requestUnlock(childFid: String) = setStatus(childFid, STATUS_PENDING_APPROVAL)

    /** Parent: approve the child's unlock request. */
    fun approveUnlock(childFid: String) = setStatus(childFid, STATUS_CLEARED)

    /** Either side: reset back to idle once the block has been lifted. */
    fun resetStatus(childFid: String) = setStatus(childFid, STATUS_NONE)

    private fun setStatus(childFid: String, status: String, extra: Map<String, Any> = emptyMap()) {
        val data = HashMap<String, Any>(extra)
        data[FIELD_PUNISHMENT_STATUS] = status
        db.collection(COLLECTION_SESSIONS).document(childFid)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.w(TAG, "setStatus($status) failed for $childFid: ${e.message}") }
    }
}
