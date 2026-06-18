package com.example.oversee.data

import android.util.Log
import com.example.oversee.data.local.PairingLogic
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firestore I/O for the ephemeral child-pairing handshake.
 * Documents live at: users/{uid}/pending_pairings/{code}
 */
object PairingRepository {

    private const val TAG = "PairingRepository"
    private const val COL_USERS = "users"
    private const val SUBCOL = "pending_pairings"

    private val db = FirebaseFirestore.getInstance()

    enum class Intent { ADD, REPLACE }

    /** Outcome of a parent looking up a code. */
    sealed class LookupResult {
        data class Valid(val code: String, val pairing: PendingPairing) : LookupResult()
        object NotFound : LookupResult()
        object Expired : LookupResult()
        object AlreadyApproved : LookupResult()
    }

    data class PendingPairing(
        val fid: String = "",
        val intent: Intent = Intent.ADD,
        val targetFid: String? = null,
        val targetName: String? = null,
        val createdAt: Long = 0L,
        val status: String = "PENDING"
    )

    private fun docRef(uid: String, code: String) =
        db.collection(COL_USERS).document(uid).collection(SUBCOL).document(code)

    private fun pairingsRef(uid: String) =
        db.collection(COL_USERS).document(uid).collection(SUBCOL)

    private fun fromSnapshot(data: Map<String, Any>?): PendingPairing? {
        data ?: return null
        val fid = data["fid"] as? String ?: return null
        val intent = runCatching { Intent.valueOf(data["intent"] as? String ?: "ADD") }
            .getOrDefault(Intent.ADD)
        return PendingPairing(
            fid = fid,
            intent = intent,
            targetFid = data["targetFid"] as? String,
            targetName = data["targetName"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            status = data["status"] as? String ?: "PENDING"
        )
    }

    /**
     * Generates a 6-digit code not currently used by a PENDING doc, then writes the pairing.
     * Calls back with the chosen code, or null on failure.
     */
    fun createPending(
        uid: String,
        fid: String,
        intent: Intent,
        targetFid: String?,
        targetName: String?,
        onResult: (String?) -> Unit
    ) {
        fun attempt(triesLeft: Int) {
            if (triesLeft <= 0) {
                Log.e(TAG, "createPending: exhausted code generation attempts")
                onResult(null)
                return
            }
            val code = PairingLogic.generateCode()
            docRef(uid, code).get()
                .addOnSuccessListener { existing ->
                    if (existing.exists()) {
                        attempt(triesLeft - 1)
                        return@addOnSuccessListener
                    }
                    val payload = hashMapOf(
                        "fid" to fid,
                        "intent" to intent.name,
                        "targetFid" to targetFid,
                        "targetName" to targetName,
                        "createdAt" to System.currentTimeMillis(),
                        "status" to "PENDING"
                    )
                    docRef(uid, code).set(payload)
                        .addOnSuccessListener { onResult(code) }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "createPending: write failed", e); onResult(null)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "createPending: existence check failed", e); onResult(null)
                }
        }
        attempt(5)
    }

    /** Live listener on a pairing doc. Fires with the current pairing, or null if it is gone. */
    fun listenPending(uid: String, code: String, onChange: (PendingPairing?) -> Unit): ListenerRegistration =
        docRef(uid, code).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "listenPending error", error); return@addSnapshotListener
            }
            onChange(if (snap != null && snap.exists()) fromSnapshot(snap.data) else null)
        }

    /** Deletes a pairing doc. Best-effort. */
    fun deletePending(uid: String, code: String, onComplete: (Boolean) -> Unit = {}) {
        docRef(uid, code).delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e -> Log.e(TAG, "deletePending failed", e); onComplete(false) }
    }

    /**
     * Parent-side lookup. Validates existence, status, and 5-min expiry.
     * Lazily deletes an expired doc.
     */
    fun lookup(uid: String, code: String, onResult: (LookupResult) -> Unit) {
        docRef(uid, code).get()
            .addOnSuccessListener { snap ->
                if (snap == null || !snap.exists()) { onResult(LookupResult.NotFound); return@addOnSuccessListener }
                val pairing = fromSnapshot(snap.data)
                if (pairing == null) { onResult(LookupResult.NotFound); return@addOnSuccessListener }
                when {
                    PairingLogic.isExpired(pairing.createdAt, System.currentTimeMillis()) -> {
                        deletePending(uid, code)
                        onResult(LookupResult.Expired)
                    }
                    pairing.status == "APPROVED" -> onResult(LookupResult.AlreadyApproved)
                    else -> onResult(LookupResult.Valid(code, pairing))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "lookup failed", e); onResult(LookupResult.NotFound)
            }
    }

    /** Parent approves: flips status to APPROVED. */
    fun approve(uid: String, code: String, onComplete: (Boolean) -> Unit) {
        docRef(uid, code).update("status", "APPROVED")
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e -> Log.e(TAG, "approve failed", e); onComplete(false) }
    }
}
