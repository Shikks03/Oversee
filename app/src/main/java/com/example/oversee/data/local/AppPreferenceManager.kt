package com.example.oversee.data.local

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

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(key, value) }
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, defaultValue)
    }

    fun saveStringSet(context: Context, key: String, value: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putStringSet(key, value) }
    }

    fun getStringSet(context: Context, key: String, defaultValue: Set<String>): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(key, defaultValue) ?: defaultValue
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    fun migrateStaleKeys(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove("device_id")
            remove("target_id")
        }
    }
}