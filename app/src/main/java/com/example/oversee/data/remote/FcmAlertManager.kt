package com.example.oversee.data.remote

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Sends a lightweight HIGH severity alert to the Cloud Function,
 * which then forwards an FCM data message to the linked parent device.
 *
 * This is separate from the Firestore data sync — it's just a push trigger.
 */
object FcmAlertManager {

    private const val TAG = "FcmAlertManager"

    private const val CLOUD_FUNCTION_URL =
        "https://us-central1-oversee-thesis.cloudfunctions.net/sendHighSeverityAlert"
    private const val ALERT_SECRET = "oversee-alert-v1"

    /**
     * Notifies the backend that a HIGH severity incident occurred on this child device.
     * Makes a fire-and-forget HTTP POST — failures are logged but do not affect incident logging.
     */
    fun sendHighSeverityAlert(context: Context) {
        val deviceId = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
            .getString("device_id", null)
            ?.takeIf { it.isNotEmpty() } ?: run {
            Log.w(TAG, "No device_id found, skipping alert")
            return
        }

        thread(name = "FcmAlert") {
            try {
                val url = java.net.URL(CLOUD_FUNCTION_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Oversee-Secret", ALERT_SECRET) // #4
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                val body = JSONObject().put("device_id", deviceId).toString() // #15
                    .toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }

                val code = connection.responseCode
                if (code == 200) {
                    Log.d(TAG, "Alert sent successfully")
                } else {
                    Log.w(TAG, "Alert returned HTTP $code")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send high severity alert", e)
            }
        }
    }
}
