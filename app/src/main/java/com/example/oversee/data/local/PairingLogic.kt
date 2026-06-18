package com.example.oversee.data.local

import com.example.oversee.data.PairingRepository.Intent
import kotlin.random.Random

/**
 * Pure, side-effect-free helpers for the child pairing flow.
 * Kept separate from PairingRepository so they are unit-testable without Firebase.
 */
object PairingLogic {

    /** Pairing codes are valid for 5 minutes from creation. */
    const val EXPIRY_MS = 5 * 60 * 1000L

    /** A random 6-digit, zero-padded pairing code, e.g. "000042" or "738201". */
    fun generateCode(random: Random = Random.Default): String =
        "%06d".format(random.nextInt(1_000_000))

    /** True once [nowMillis] has reached or passed [createdAtMillis] + TTL. */
    fun isExpired(createdAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - createdAtMillis >= EXPIRY_MS

    /** Text shown to the parent to describe what they are approving. */
    fun intentSummary(intent: Intent, targetName: String?): String = when (intent) {
        Intent.ADD ->
            "Add this device as a new child?"
        Intent.REPLACE ->
            "Replace \"${targetName ?: "this child"}\" with this device? " +
                "Its existing logs will be permanently deleted."
    }

    /** Default display name for a brand-new child given how many already exist. */
    fun defaultChildName(existingCount: Int): String = "Child ${existingCount + 1}"
}
