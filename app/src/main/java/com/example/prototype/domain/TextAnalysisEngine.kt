package com.example.prototype.domain

import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * Combined text preprocessing and Levenshtein fuzzy-matching pipeline.
 *
 * Usage:
 *   val engine = TextAnalysisEngine.fromAssets(context)  // production
 *   val engine = TextAnalysisEngine.withWords(setOf(...)) // tests
 *   val results: List<DetectedWord> = engine.analyze(ocrText)
 */
class TextAnalysisEngine private constructor(
    private val referenceWords: Set<String>
) {

    companion object {

        private val ASSET_FILES = listOf(
            "inappropriate_words_english.txt",
            "inappropriate_words_tagalog.txt"
        )

        /** Production entry point — loads both word lists from assets. */
        fun fromAssets(context: Context): TextAnalysisEngine {
            val words = mutableSetOf<String>()
            ASSET_FILES.forEach { filename ->
                context.assets.open(filename).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val word = line.trim().lowercase(Locale.getDefault())
                        if (word.isNotEmpty()) words.add(word)
                    }
                }
            }
            return TextAnalysisEngine(words)
        }

        /** Test entry point — inject a pre-built word set directly. */
        fun withWords(words: Set<String>): TextAnalysisEngine = TextAnalysisEngine(words)
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    data class DetectedWord(
        val rawToken: String,         // the normalized token
        val matchedWord: String,      // the reference word it matched
        val similarityScore: Float,   // 1 - (dist / max(token.len, ref.len))
        val levenshteinDistance: Int
    )

    /**
     * Run the full two-stage pipeline on [ocrText] and return every detected
     * inappropriate token. Empty list means no matches (clean).
     */
    fun analyze(ocrText: String): List<DetectedWord> {
        // 1ST PART: TEXT PREPROCESSING
        val tokens = tokenize(ocrText)
            .map { normalizeToken(it) }
            .filter { it.length > 2 }   // short-token filter: keep only length > 2

        // 2ND PART: LEVENSHTEIN SIMILARITY ANALYSIS
        val detected = mutableListOf<DetectedWord>()

        for (token in tokens) {
            if (token.isEmpty()) continue

            var bestMatch: DetectedWord? = null

            for (refWord in referenceWords) {
                val dist = levenshteinDistance(token, refWord)
                val sim  = similarityScore(token, refWord, dist)

                if (isFuzzyMatch(token.length, dist, sim)) {
                    if (bestMatch == null || sim > bestMatch.similarityScore) {
                        bestMatch = DetectedWord(
                            rawToken          = token,
                            matchedWord       = refWord,
                            similarityScore   = sim,
                            levenshteinDistance = dist
                        )
                    }
                }
            }

            if (bestMatch != null) detected.add(bestMatch)
        }

        return detected
    }

    // ─────────────────────────────────────────────────────────────
    // STAGE 1 — Private helpers
    // ─────────────────────────────────────────────────────────────

    /** Step 1 — Split raw OCR text into individual tokens on any whitespace. */
    private fun tokenize(raw: String): List<String> =
        raw.split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * Step 2 + 3 — Per-character normalization followed by final cleanup.
     *
     * Order of operations:
     *   2b) Strip trailing '!' characters first (so leftover '!' in middle converts to 'i')
     *   2a) Map leet/symbol characters to their letter equivalents
     *   2c) Collapse runs of 3+ consecutive identical characters to max 2
     *   3)  Trim, remove remaining non-alphanumeric symbols, lowercase
     */
    private fun normalizeToken(token: String): String {
        // 2b — Remove trailing '!' (may be multiple: "idiot!!" → "idiot")
        var working = token
        while (working.endsWith('!')) {
            working = working.dropLast(1)
        }

        // 2a + 2c — Single-pass char iteration: leet substitution + repeat collapse
        val output = StringBuilder()
        for (ch in working) {
            val mapped = leetMap[ch] ?: ch   // 2a: substitute or keep as-is

            // 2c: if appending this char would create a 3rd consecutive identical char, skip it
            if (output.length >= 2 &&
                output[output.length - 1] == mapped &&
                output[output.length - 2] == mapped
            ) {
                continue
            }

            output.append(mapped)
        }

        // 3 — Final cleanup: trim, strip remaining non-alphanumeric, lowercase
        return output
            .toString()
            .trim()
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .lowercase(Locale.getDefault())
    }

    // ─────────────────────────────────────────────────────────────
    // STAGE 2 — Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Standard two-row dynamic programming Levenshtein distance.
     * Cost: insertion = 1, deletion = 1, substitution = 1.
     * Time:  O(|a| × |b|)
     * Space: O(min(|a|, |b|))
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b)       return 0
        if (a.isEmpty())  return b.length
        if (b.isEmpty())  return a.length

        // Ensure b is the shorter string to minimise array allocation
        if (a.length < b.length) return levenshteinDistance(b, a)

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,        // insertion
                    prev[j] + 1,            // deletion
                    prev[j - 1] + cost      // substitution
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }

        return prev[b.length]
    }

    /**
     * Normalized similarity score in [0.0, 1.0].
     * Sim(a, b) = 1 - dist / max(|a|, |b|)
     */
    private fun similarityScore(a: String, b: String, distance: Int): Float {
        val maxLen = max(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - distance.toFloat() / maxLen
    }

    /**
     * Threshold rule:
     *   token.length <= 4 → exact match only (distance == 0)
     *   token.length >= 5 → fuzzy match if sim >= 0.60
     */
    private fun isFuzzyMatch(tokenLen: Int, distance: Int, sim: Float): Boolean =
        if (tokenLen <= 4) distance == 0 else sim >= 0.60f

    private val leetMap: Map<Char, Char> = mapOf(
        '0' to 'o',
        '1' to 'i',
        '3' to 'e',
        '4' to 'a',
        '5' to 's',
        '@' to 'a',
        '$' to 's',
        '!' to 'i'
    )
}
