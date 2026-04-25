package com.example.oversee.domain

import android.content.Context
import java.util.Locale
import kotlin.math.abs
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
    private val singleWordRefs: Set<String>,
    private val multiWordRefs: Set<List<String>>
) {

    companion object {

        private val ASSET_FILES = listOf(
            "inappropriate_words_english.txt",
            "inappropriate_words_tagalog.txt"
        )

        /** Production entry point — loads both word lists from assets. */
        fun fromAssets(context: Context): TextAnalysisEngine {
            val singles = mutableSetOf<String>()
            val multis = mutableSetOf<List<String>>()
            ASSET_FILES.forEach { filename ->
                context.assets.open(filename).bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim().lowercase(Locale.getDefault())
                        if (line.isEmpty()) return@forEach
                        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        when {
                            parts.size == 1 -> singles.add(parts[0])
                            parts.size >= 2 -> multis.add(parts)
                        }
                    }
                }
            }
            return TextAnalysisEngine(singles, multis)
        }

        /** Test entry point — inject a pre-built single-word set directly. */
        fun withWords(words: Set<String>): TextAnalysisEngine = TextAnalysisEngine(words, emptySet())

        /** Test entry point — inject both single-word and multi-word sets. */
        fun withWords(singles: Set<String>, multis: Set<List<String>>): TextAnalysisEngine =
            TextAnalysisEngine(singles, multis)
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    data class DetectedWord(
        val originalText: String,        // NEW: The exact text from the screen (e.g., "T@ng1n@")
        val rawToken: String,            // the normalized token (lowercased, cleaned)
        val matchedWord: String,         // the reference word it matched
        val originalText: String,
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

            // Cross-language collision filter — skip benign function words before fuzzy matching.
            // android.util.Log is wrapped in runCatching so JVM unit tests don't crash on the
            // "Method not mocked" path (no Robolectric in this project).
            if (WordFilterLists.isCollisionFilterWord(norm.text)) {
                runCatching {
                    android.util.Log.d("TextAnalysisEngine", "Skipped by collision filter: ${norm.text}")
                }
                continue
            }
            // Pronouns are kept in rawTokens (scorer reads them for proximity) but never flagged.
            if (WordFilterLists.isPersonTargetingPronoun(norm.text)) continue

            val hasObfuscationSignal = norm.hasRepetition || norm.wasLeetApplied
            var bestMatch: DetectedWord? = null

            for (refWord in singleWordRefs) {
                val dist = levenshteinDistance(norm.text, refWord)
                val sim = similarityScore(norm.text, refWord, dist)

                if (isFuzzyMatch(norm.text, refWord, dist, hasObfuscationSignal)) {
                    if (bestMatch == null || sim > bestMatch.similarityScore) {
                        bestMatch = DetectedWord(
                            originalText = raw,
                            rawToken = norm.text,
                            matchedWord = refWord,
                            originalText = raw,
                            similarityScore = sim,
                            levenshteinDistance = dist,
                            tokenIndex = idx,
                            isAllCaps = norm.isAllCaps,
                            hasPunctuation = norm.hasPunctuation,
                            hasRepetition = norm.hasRepetition
                        )
                    }
                }
            }

            if (bestMatch != null) detected.add(bestMatch)
        }

        // BIGRAM PASS — multi-word refs and OCR-split single refs
        val consumedByPhrase = mutableSetOf<Int>()
        val phraseDetections = mutableListOf<DetectedWord>()

        // First loop: bigram concat vs multiWordRefs (2-word only)
        for (i in 0 until rawTokens.size - 1) {
            val normA = normalizeToken(rawTokens[i])
            val normB = normalizeToken(rawTokens[i + 1])
            val concat = normA.text + normB.text
            if (concat.length < 4) continue

            for (refParts in multiWordRefs) {
                if (refParts.size != 2) continue
                val refConcat = refParts.joinToString("")
                val dist = levenshteinDistance(concat, refConcat)
                val sim = similarityScore(concat, refConcat, dist)
                val obf = normA.hasRepetition || normB.hasRepetition ||
                    normA.wasLeetApplied || normB.wasLeetApplied
                if (isFuzzyMatch(concat, refConcat, dist, obf)) {
                    phraseDetections.add(
                        DetectedWord(
                            originalText = "${rawTokens[i]} ${rawTokens[i + 1]}", // <--- PASS ORIGINAL PHRASE
                            rawToken = concat,
                            matchedWord = refParts.joinToString(" "),
                            originalText = "${rawTokens[i]} ${rawTokens[i + 1]}",
                            similarityScore = sim,
                            levenshteinDistance = dist,
                            tokenIndex = i,
                            isAllCaps = normA.isAllCaps && normB.isAllCaps,
                            hasPunctuation = normB.hasPunctuation,
                            hasRepetition = normA.hasRepetition || normB.hasRepetition
                        )
                    )
                    consumedByPhrase += i
                    consumedByPhrase += i + 1
                    break
                }
            }
        }

        // Second loop: bigram concat vs long singleWordRefs (OCR-split reconstitution)
        for (i in 0 until rawTokens.size - 1) {
            if (i in consumedByPhrase || (i + 1) in consumedByPhrase) continue
            val normA = normalizeToken(rawTokens[i])
            val normB = normalizeToken(rawTokens[i + 1])
            val concat = normA.text + normB.text
            if (concat.length < 5) continue

            for (refWord in singleWordRefs) {
                if (refWord.length < 6) continue
                val dist = levenshteinDistance(concat, refWord)
                val obf = normA.hasRepetition || normB.hasRepetition ||
                    normA.wasLeetApplied || normB.wasLeetApplied
                if (isFuzzyMatch(concat, refWord, dist, obf)) {
                    phraseDetections.add(
                        DetectedWord(
                            originalText = "${rawTokens[i]} ${rawTokens[i + 1]}", // <--- PASS ORIGINAL PHRASE
                            rawToken = concat,
                            matchedWord = refWord,
                            originalText = "${rawTokens[i]} ${rawTokens[i + 1]}",
                            similarityScore = similarityScore(concat, refWord, dist),
                            levenshteinDistance = dist,
                            tokenIndex = i,
                            isAllCaps = normA.isAllCaps && normB.isAllCaps,
                            hasPunctuation = normB.hasPunctuation,
                            hasRepetition = normA.hasRepetition || normB.hasRepetition
                        )
                    )
                    consumedByPhrase += i
                    consumedByPhrase += i + 1
                    break
                }
            }
        }

        val finalDetected = detected.filter { it.tokenIndex !in consumedByPhrase } + phraseDetections
        return AnalysisResult(rawTokens, finalDetected.sortedBy { it.tokenIndex })
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
     * [wasLeetApplied] — at least one leet substitution was made (private signal, not on DetectedWord)
     */
    private data class NormalizedToken(
        val text: String,
        val isAllCaps: Boolean,
        val hasPunctuation: Boolean,
        val hasRepetition: Boolean,
        val wasLeetApplied: Boolean
    )

    /**
     * Per-token normalization pipeline. Strict ordering:
     *
     *  1. Check hasPunctuation on the ORIGINAL token.
     *  2. Strip trailing '!' characters.
     *  3. Leet substitution — UPPERCASE map so caps detection survives. Track whether any sub was made.
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

        // Step 3 — Leet substitution (uppercase map); track whether any char was mapped
        var wasLeetApplied = false
        val afterLeet = StringBuilder(working.length)
        for (ch in working) {
            val mapped = leetMap[ch]
            if (mapped != null) {
                wasLeetApplied = true
                afterLeet.append(mapped)
            } else {
                afterLeet.append(ch)
            }
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
            text = finalText,
            isAllCaps = isAllCaps,
            hasPunctuation = hasPunctuation,
            hasRepetition = hasRepetition,
            wasLeetApplied = wasLeetApplied
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
        if (a == b) return 0
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
     * Threshold rules with first-letter anchor and length-delta guard:
     *   token.length <= 4 → exact match only (distance == 0)
     *   token.length 5–6  → exact match, or dist 1 only if obfuscation signal present
     *   token.length 7+   → distance <= 2
     */
    private fun isFuzzyMatch(
        tokenText: String,
        refWord: String,
        distance: Int,
        hasObfuscationSignal: Boolean
    ): Boolean {
        if (tokenText.isEmpty() || refWord.isEmpty()) return false
        if (tokenText[0] != refWord[0]) return false
        if (abs(tokenText.length - refWord.length) > 2) return false
        return when {
            tokenText.length <= 4 -> distance == 0
            tokenText.length <= 6 -> distance == 0 || (distance == 1 && hasObfuscationSignal)
            else -> distance <= 2
        }
    }

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
