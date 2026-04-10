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
 *   val result: AnalysisResult = engine.analyze(ocrText)
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
        val rawToken: String,            // the normalized token (lowercased, cleaned)
        val matchedWord: String,         // the reference word it matched
        val similarityScore: Float,      // 1 - (dist / max(token.len, ref.len))
        val levenshteinDistance: Int,
        val tokenIndex: Int,             // position in AnalysisResult.rawTokens (pre-filter)
        val isAllCaps: Boolean,          // true if all alpha chars were uppercase after leet sub
        val hasPunctuation: Boolean,     // true if original token ended with ! ? or .
        val hasRepetition: Boolean       // true if original token had 3+ consecutive identical chars
    )

    data class AnalysisResult(
        val rawTokens: List<String>,     // all original tokens before normalization
        val detectedWords: List<DetectedWord>
    )

    /**
     * Run the full two-stage pipeline on [ocrText] and return an [AnalysisResult]
     * containing the original token list and every detected inappropriate token.
     */
    fun analyze(ocrText: String): AnalysisResult {
        // 1ST PART: TOKENIZE — keep raw tokens for index tracking and proximity checks
        val rawTokens = tokenize(ocrText)

        // 2ND PART: NORMALIZE + LEVENSHTEIN SIMILARITY ANALYSIS
        val detected = mutableListOf<DetectedWord>()

        for ((idx, raw) in rawTokens.withIndex()) {
            val norm = normalizeToken(raw)
            if (norm.text.length <= 2) continue   // short-token filter: keep only length > 2

            var bestMatch: DetectedWord? = null

            for (refWord in referenceWords) {
                val dist = levenshteinDistance(norm.text, refWord)
                val sim  = similarityScore(norm.text, refWord, dist)

                if (isFuzzyMatch(norm.text.length, dist, sim)) {
                    if (bestMatch == null || sim > bestMatch.similarityScore) {
                        bestMatch = DetectedWord(
                            rawToken            = norm.text,
                            matchedWord         = refWord,
                            similarityScore     = sim,
                            levenshteinDistance = dist,
                            tokenIndex          = idx,
                            isAllCaps           = norm.isAllCaps,
                            hasPunctuation      = norm.hasPunctuation,
                            hasRepetition       = norm.hasRepetition
                        )
                    }
                }
            }

            if (bestMatch != null) detected.add(bestMatch)
        }

        return AnalysisResult(rawTokens, detected)
    }

    // ─────────────────────────────────────────────────────────────
    // STAGE 1 — Private helpers
    // ─────────────────────────────────────────────────────────────

    /** Split raw OCR text into individual tokens on any whitespace. */
    private fun tokenize(raw: String): List<String> =
        raw.split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * Carrier for the results of a single-pass normalization.
     *
     * [text]           — fully normalized, lowercased — feeds Levenshtein
     * [isAllCaps]      — all alphabetic chars were uppercase after leet substitution
     * [hasPunctuation] — original token ended with '!', '?', or '.'
     * [hasRepetition]  — post-leet string contained 3+ consecutive identical chars
     */
    private data class NormalizedToken(
        val text: String,
        val isAllCaps: Boolean,
        val hasPunctuation: Boolean,
        val hasRepetition: Boolean
    )

    /**
     * Per-token normalization pipeline. Strict ordering:
     *
     *  1. Check hasPunctuation on the ORIGINAL token.
     *  2. Strip trailing '!' characters.
     *  3. Leet substitution — UPPERCASE map so caps detection survives.
     *  4. Check hasRepetition (3+ consecutive identical chars, post-leet, pre-collapse).
     *  5. Collapse runs of 3+ identical chars down to 2.
     *  6. Strip remaining non-alphanumeric characters.
     *  7. Check isAllCaps (≥ 2 alpha chars, all uppercase).
     *  8. Lowercase — absolute last step before returning.
     */
    private fun normalizeToken(token: String): NormalizedToken {
        // Step 1 — hasPunctuation on original token
        val hasPunctuation = token.endsWith('!') || token.endsWith('?') || token.endsWith('.')

        // Step 2 — Strip trailing '!'
        var working = token
        while (working.endsWith('!')) {
            working = working.dropLast(1)
        }

        // Step 3 — Leet substitution (uppercase map)
        val afterLeet = StringBuilder(working.length)
        for (ch in working) {
            afterLeet.append(leetMap[ch] ?: ch)
        }
        val leetStr = afterLeet.toString()

        // Step 4 — Detect 3+ consecutive identical chars (post-leet, pre-collapse)
        val hasRepetition = leetStr.contains(Regex("(.)\\1\\1"))

        // Step 5 — Collapse 3+ consecutive identical chars → max 2
        val collapsed = StringBuilder(leetStr.length)
        for (ch in leetStr) {
            if (collapsed.length >= 2 &&
                collapsed[collapsed.length - 1] == ch &&
                collapsed[collapsed.length - 2] == ch
            ) {
                continue
            }
            collapsed.append(ch)
        }

        // Step 6 — Strip non-alphanumeric
        val stripped = collapsed.toString().trim().replace(Regex("[^a-zA-Z0-9]"), "")

        // Step 7 — isAllCaps: at least 2 alpha chars, all uppercase
        val alphaChars = stripped.filter { it.isLetter() }
        val isAllCaps = alphaChars.length >= 2 && alphaChars.all { it.isUpperCase() }

        // Step 8 — Lowercase (last step)
        val finalText = stripped.lowercase(Locale.getDefault())

        return NormalizedToken(
            text           = finalText,
            isAllCaps      = isAllCaps,
            hasPunctuation = hasPunctuation,
            hasRepetition  = hasRepetition
        )
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
        if (a == b)      return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Ensure b is the shorter string to minimise array allocation
        if (a.length < b.length) return levenshteinDistance(b, a)

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,     // insertion
                    prev[j] + 1,         // deletion
                    prev[j - 1] + cost   // substitution
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
        '0' to 'O',
        '1' to 'I',
        '3' to 'E',
        '4' to 'A',
        '5' to 'S',
        '@' to 'A',
        '$' to 'S',
        '!' to 'I'
    )
}
