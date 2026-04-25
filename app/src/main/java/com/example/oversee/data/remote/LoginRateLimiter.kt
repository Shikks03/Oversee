package com.example.oversee.data.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.concurrent.thread
import kotlin.math.ceil

/**
 * IP-based server-side login rate limiter.
 *
 * Tracks failed login attempts in Firestore keyed by public IP address.
 * After MAX_ATTEMPTS failures, login is blocked for LOCKOUT_DURATION_MS.
 * Counter auto-resets if INACTIVITY_RESET_MS has elapsed since the last failure.
 * Lockout survives app reinstalls since it lives in Firestore, not on-device storage.
 */
object LoginRateLimiter {

    private const val TAG = "LoginRateLimiter"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 10 * 60 * 1000L
    private const val INACTIVITY_RESET_MS = 10 * 60 * 1000L
    private const val IP_URL = "https://api.ipify.org"
    private const val COLLECTION = "login_attempts"

    private val db = FirebaseFirestore.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Cached for the process lifetime; clears on app restart. */
    private var cachedIp: String? = null

    /**
     * Fetches the device's public IP and checks Firestore for an active lockout.
     *
     * Calls [onBlocked] with a user-facing message if the device is locked out
     * or if the network / Firestore is unreachable (fail-closed).
     * Calls [onReady] with the IP string if login can proceed.
     */
    fun checkAndProceed(onBlocked: (String) -> Unit, onReady: (ip: String) -> Unit) {
        fetchPublicIp { ip ->
            if (ip == null) {
                onBlocked("Unable to verify your network. Please check your connection and try again.")
                return@fetchPublicIp
            }
            val docId = sanitizeIp(ip)
            db.collection(COLLECTION).document(docId).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        onReady(ip)
                        return@addOnSuccessListener
                    }
                    val now = System.currentTimeMillis()
                    val lockoutUntil = doc.getLong("lockout_until") ?: 0L
                    val lastFailTime = doc.getLong("last_fail_time") ?: 0L

                    when {
                        now < lockoutUntil -> {
                            val msLeft = lockoutUntil - now
                            val msg = if (msLeft < 60_000) {
                                "Too many attempts. Try again in less than a minute."
                            } else {
                                val minutes = ceil(msLeft / 60_000.0).toInt()
                                "Too many attempts. Try again in $minutes minute(s)."
                            }
                            onBlocked(msg)
                        }
                        else -> onReady(ip)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore read failed in checkAndProceed", e)
                    onBlocked("Unable to verify login status. Please try again later.")
                }
        }
    }

    /**
     * Records a failed login attempt for this IP.
     *
     * Resets the counter first if [INACTIVITY_RESET_MS] has elapsed since the last failure.
     * Calls [onResult] with (attemptsLeft, null) on a normal failure, or (0, lockoutMsg)
     * when the lockout threshold is reached.
     * On Firestore error, calls [onResult] with (null, "Incorrect credentials.") so the
     * caller still shows a reasonable message (auth itself already failed).
     */
    fun recordFailedAttempt(ip: String, onResult: (attemptsLeft: Int?, lockoutMsg: String?) -> Unit) {
        val docId = sanitizeIp(ip)
        db.collection(COLLECTION).document(docId).get()
            .addOnSuccessListener { doc ->
                val now = System.currentTimeMillis()
                val currentCount = doc.getLong("fail_count")?.toInt() ?: 0
                val lastFailTime = doc.getLong("last_fail_time") ?: 0L

                val newCount = if (lastFailTime > 0L && (now - lastFailTime) > INACTIVITY_RESET_MS) {
                    1
                } else {
                    currentCount + 1
                }

                val lockoutUntil = if (newCount >= MAX_ATTEMPTS) now + LOCKOUT_DURATION_MS else 0L

                val data = hashMapOf(
                    "fail_count" to newCount.toLong(),
                    "last_fail_time" to now,
                    "lockout_until" to lockoutUntil
                )

                db.collection(COLLECTION).document(docId).set(data)
                    .addOnSuccessListener {
                        if (newCount >= MAX_ATTEMPTS) {
                            onResult(0, "Too many attempts. Try again in 10 minutes.")
                        } else {
                            onResult(MAX_ATTEMPTS - newCount, null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore write failed in recordFailedAttempt", e)
                        onResult(null, "Incorrect credentials.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore read failed in recordFailedAttempt", e)
                onResult(null, "Incorrect credentials.")
            }
    }

    /**
     * Deletes the lockout document for this IP on successful login.
     * Fire-and-forget — login already succeeded, so failures are only logged.
     */
    fun resetAttempts(ip: String) {
        val docId = sanitizeIp(ip)
        db.collection(COLLECTION).document(docId).delete()
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to reset login attempts for $docId", e)
            }
    }

    /**
     * Fetches the device's public IPv4 address via api.ipify.org.
     * Result is cached in memory for the process lifetime.
     * Runs network I/O on a dedicated background thread; [onResult] is always
     * delivered on the main thread so callers can safely touch UI.
     */
    private fun fetchPublicIp(onResult: (String?) -> Unit) {
        cachedIp?.let { onResult(it); return }
        thread(name = "IpFetch") {
            val ip = try {
                val url = java.net.URL(IP_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
                val result = connection.inputStream.bufferedReader().readText().trim()
                connection.disconnect()
                cachedIp = result
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch public IP", e)
                null
            }
            mainHandler.post { onResult(ip) }
        }
    }

    /** Converts an IP string to a safe Firestore document ID (dots → underscores, colons → dashes). */
    private fun sanitizeIp(raw: String): String =
        raw.replace('.', '_').replace(':', '-')
}
