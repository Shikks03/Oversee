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
        private const val PREF_COLLISION_FILTER = "cached_collision_filter"
        private const val PREF_PERSON_PRONOUNS = "cached_person_pronouns"
        private const val PREF_LAST_SYNC = "word_list_last_sync"
        private const val COLLECTION = "word_lists"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun isDueSynced(): Boolean {
        val lastSync = prefs.getLong(PREF_LAST_SYNC, 0L)
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }

    /** True if the whitelist cache has never been populated — forces a sync regardless of the time interval. */
    fun isMissingFilterCache(): Boolean =
        prefs.getString(PREF_COLLISION_FILTER, null) == null ||
        prefs.getString(PREF_PERSON_PRONOUNS, null) == null

    /**
     * Returns cached blacklist word lists synchronously.
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
     * Returns cached whitelist sets synchronously.
     * Falls back to the hardcoded defaults in WordFilterLists if no Firestore cache exists yet.
     */
    fun getCachedFilterLists(): Pair<Set<String>, Set<String>> {
        val collisionFilter = prefs.getString(PREF_COLLISION_FILTER, null)
            ?.lines()?.filter { it.isNotBlank() }?.toSet()
            ?: WordFilterLists.DEFAULT_COLLISION_FILTER_WORDS
        val pronouns = prefs.getString(PREF_PERSON_PRONOUNS, null)
            ?.lines()?.filter { it.isNotBlank() }?.toSet()
            ?: WordFilterLists.DEFAULT_PERSON_TARGETING_PRONOUNS
        return collisionFilter to pronouns
    }

    /**
     * Fetches all word lists (blacklist + whitelist) from Firestore and updates the local cache.
     * If Firestore documents don't exist yet, seeds them from bundled assets / hardcoded defaults.
     * Returns true if any cached list changed (caller should rebuild the engine / update filters).
     */
    suspend fun syncFromFirestore(): Boolean {
        var changed = false
        try {
            val engDoc = firestore.collection(COLLECTION).document("english").get().await()
            val tagDoc = firestore.collection(COLLECTION).document("tagalog").get().await()
            val cfDoc  = firestore.collection(COLLECTION).document("collision_filter").get().await()
            val ppDoc  = firestore.collection(COLLECTION).document("person_pronouns").get().await()

            @Suppress("UNCHECKED_CAST")
            val engWords = (engDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST")
            val tagWords = (tagDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST")
            val cfWords  = (cfDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST")
            val ppWords  = (ppDoc.get("words") as? List<String>)?.filter { it.isNotBlank() }

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

            if (!cfWords.isNullOrEmpty()) {
                val serialized = cfWords.joinToString("\n")
                if (prefs.getString(PREF_COLLISION_FILTER, null) != serialized) {
                    prefs.edit().putString(PREF_COLLISION_FILTER, serialized).apply()
                    changed = true
                }
            } else {
                Log.i(TAG, "collision_filter not in Firestore — seeding from defaults")
                seedToFirestore("collision_filter", WordFilterLists.DEFAULT_COLLISION_FILTER_WORDS.toList())
            }

            if (!ppWords.isNullOrEmpty()) {
                val serialized = ppWords.joinToString("\n")
                if (prefs.getString(PREF_PERSON_PRONOUNS, null) != serialized) {
                    prefs.edit().putString(PREF_PERSON_PRONOUNS, serialized).apply()
                    changed = true
                }
            } else {
                Log.i(TAG, "person_pronouns not in Firestore — seeding from defaults")
                seedToFirestore("person_pronouns", WordFilterLists.DEFAULT_PERSON_TARGETING_PRONOUNS.toList())
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
