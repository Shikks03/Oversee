package com.example.prototype.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Technical manager for SharedPreferences (AppConfig).
 * The Repository calls this to save/load settings.
 */
object AppPreferenceManager {
    private const val PREFS_NAME = "AppConfig"

    fun saveString(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(key, value) }
    }

    fun getString(context: Context, key: String, defaultValue: String): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, defaultValue) ?: defaultValue
    }

    fun saveLong(context: Context, key: String, value: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putLong(key, value) }
    }

    fun getLong(context: Context, key: String, defaultValue: Long): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(key, defaultValue)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}