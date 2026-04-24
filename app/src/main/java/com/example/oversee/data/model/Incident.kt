package com.example.oversee.data.model

/**
 * Represents a single detected monitoring event.
 *
 * This data class is immutable (val) to ensure thread safety when passing
 * data between the Service (background) and the UI (main thread).
 *
 * @property rawWord The raw word detected (e.g., "stup1d!").
 * @property matchedWord The word matched.
 * @property severity The classification level ("HIGH", "MEDIUM", "LOW").
 * @property appName The package name or label of the app where it was found.
 * @property timestamp The exact time of detection (Unix Epoch ms).
 */
data class Incident(
    val rawWord: String,
    val matchedWord: String,
    val severity: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)