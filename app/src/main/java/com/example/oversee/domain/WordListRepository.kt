package com.example.oversee.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale

class WordListRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("word_list_cache", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "WordListRepository"
        private const val PREF_ENGLISH = "cached_english_words"
        private const val PREF_TAGALOG = "cached_tagalog_words"
        private const val PREF_LAST_SYNC = "word_list_last_sync"
        private const val COLLECTION = "word_lists"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun isDueSynced(): Boolean {
        val lastSync = prefs.getLong(PREF_LAST_SYNC, 0L)
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }

    /**
     * Returns cached word lists synchronously.
     * Falls back to bundled assets if no Firestore cache exists yet.
     */
    fun getCachedWords(): Pair<List<String>, List<String>> {
        val english = prefs.getString(PREF_ENGLISH, null)
            ?.lines()?.filter { it.isNotBlank() }
            ?: loadFromAssets("inappropriate_words_english.txt")
        val tagalog = prefs.getString(PREF_TAGALOG, null)
            ?.lines()?.filter { it.isNotBlank() }
            ?: loadFromAssets("inappropriate_words_tagalog.txt")
        return english to tagalog
    }

    /**
     * Fetches word lists from Firestore and updates the local cache.
     * If Firestore documents don't exist yet, seeds them from the bundled assets.
     * Returns true if the cached lists changed (caller should rebuild the engine).
     */
    suspend fun syncFromFirestore(): Boolean {
        var changed = false
        try {
            val engDoc = firestore.collection(COLLECTION).document("english").get().await()
            val tagDoc = firestore.collection(COLLECTION).document("tagalog").get().await()

            @Suppress("UNCHECKED_CAST")
            val engWords = (engDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST")
            val tagWords = (tagDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }

            if (!engWords.isNullOrEmpty()) {
                val serialized = engWords.joinToString("\n")
                if (prefs.getString(PREF_ENGLISH, null) != serialized) {
                    prefs.edit().putString(PREF_ENGLISH, serialized).apply()
                    changed = true
                }
            } else {
                Log.i(TAG, "english word list not in Firestore — seeding from assets")
                seedToFirestore("english", loadFromAssets("inappropriate_words_english.txt"))
            }

            if (!tagWords.isNullOrEmpty()) {
                val serialized = tagWords.joinToString("\n")
                if (prefs.getString(PREF_TAGALOG, null) != serialized) {
                    prefs.edit().putString(PREF_TAGALOG, serialized).apply()
                    changed = true
                }
            } else {
                Log.i(TAG, "tagalog word list not in Firestore — seeding from assets")
                seedToFirestore("tagalog", loadFromAssets("inappropriate_words_tagalog.txt"))
            }
            prefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync failed — using cached/asset words: ${e.message}")
        }
        return changed
    }

    private suspend fun seedToFirestore(language: String, words: List<String>) {
        try {
            firestore.collection(COLLECTION).document(language)
                .set(mapOf("words" to words))
                .await()
            Log.i(TAG, "Seeded $language (${words.size} words) to Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed $language: ${e.message}")
        }
    }

    private fun loadFromAssets(filename: String): List<String> =
        context.assets.open(filename).bufferedReader().readLines()
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotEmpty() }
}
