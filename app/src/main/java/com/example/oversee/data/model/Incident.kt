package com.example.oversee.data.model

/**
 * Represents a single detected monitoring event.
 *
 * This data class is immutable (val) to ensure thread safety when passing
 * data between the Service (background) and the UI (main thread).
 *
 * @property word The specific keyword detected (e.g., "scam").
 * @property severity The classification level ("HIGH", "MEDIUM", "LOW").
 * @property appName The package name or label of the app where it was found.
 * @property timestamp The exact time of detection (Unix Epoch ms).
 */
data class Incident(
    val word: String,
    val severity: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)