package com.example.oversee.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AppPreferenceManager {
    private const val PREFS_NAME = "AppConfig"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: buildEncryptedPrefs(context).also { encryptedPrefs = it }
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveString(context: Context, key: String, value: String) {
        getPrefs(context).edit { putString(key, value) }
    }

    fun getString(context: Context, key: String, defaultValue: String): String {
        return getPrefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun saveLong(context: Context, key: String, value: Long) {
        getPrefs(context).edit { putLong(key, value) }
    }

    fun getLong(context: Context, key: String, defaultValue: Long): Long {
        return getPrefs(context).getLong(key, defaultValue)
    }

    fun saveBoolean(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit { putBoolean(key, value) }
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return getPrefs(context).getBoolean(key, defaultValue)
    }

    fun saveStringSet(context: Context, key: String, value: Set<String>) {
        getPrefs(context).edit { putStringSet(key, value) }
    }

    fun getStringSet(context: Context, key: String, defaultValue: Set<String>): Set<String> {
        return getPrefs(context).getStringSet(key, defaultValue) ?: defaultValue
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit { clear() }
    }

    fun migrateStaleKeys(context: Context) {
        getPrefs(context).edit {
            remove("device_id")
            remove("target_id")
        }
    }
}