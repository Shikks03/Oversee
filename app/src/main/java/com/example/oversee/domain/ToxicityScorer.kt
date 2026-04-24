package com.example.oversee.domain

import java.util.Locale

object ToxicityScorer {

    private val personDirectedWords = setOf("you", "ikaw", "ka", "mo", "u")

    private const val PROXIMITY_WINDOW    = 2
    private const val FREQUENCY_THRESHOLD = 2   // word must appear MORE than this many times

    data class ScoredWord(
        val rawToken: String,
        val matchedWord: String,
        val originalText: String,
        val toxicityScore: Int,          // 1..6
        val severity: String,            // "Mild" | "Moderate" | "Severe"
        val isAllCaps: Boolean,
        val hasPunctuation: Boolean,
        val hasRepetition: Boolean,
        val isFrequent: Boolean,
        val isPersonDirected: Boolean,
        val similarityScore: Float,
        val levenshteinDistance: Int
    )

    /**
     * Score every detected word in [analysisResult] using 5 additive rules.
     *
     * Rule 1 (+1): isAllCaps
     * Rule 2 (+1): hasPunctuation
     * Rule 3 (+1): hasRepetition
     * Rule 4 (+1): same matchedWord appears > FREQUENCY_THRESHOLD times in this screenshot
     * Rule 5 (+1): a person-directed word appears within ±PROXIMITY_WINDOW tokens
     *
     * Baseline: 1 (word was flagged at all)
     * Max score: 6 → "Severe"
     */
    fun score(analysisResult: TextAnalysisEngine.AnalysisResult): List<ScoredWord> {
        val freqMap = analysisResult.detectedWords
            .groupingBy { it.matchedWord }
            .eachCount()

        return analysisResult.detectedWords.map { dw ->
            var s = 1                              // baseline

            if (dw.isAllCaps)      s++
            if (dw.hasPunctuation) s++
            if (dw.hasRepetition)  s++

            val isFrequent = (freqMap[dw.matchedWord] ?: 0) > FREQUENCY_THRESHOLD
            if (isFrequent) s++

            val isPersonDirected = checkPersonDirected(dw.tokenIndex, analysisResult.rawTokens)
            if (isPersonDirected) s++

            ScoredWord(
                rawToken          = dw.rawToken,
                matchedWord       = dw.matchedWord,
                originalText      = dw.originalText,
                toxicityScore     = s,
                severity          = classifySeverity(s),
                isAllCaps         = dw.isAllCaps,
                hasPunctuation    = dw.hasPunctuation,
                hasRepetition     = dw.hasRepetition,
                isFrequent        = isFrequent,
                isPersonDirected  = isPersonDirected,
                similarityScore   = dw.similarityScore,
                levenshteinDistance = dw.levenshteinDistance
            )
        }
    }

    /**
     * Returns true if any token within ±[PROXIMITY_WINDOW] positions of [tokenIndex]
     * (excluding [tokenIndex] itself) is a person-directed word.
     * Comparison is whole-token, case-insensitive.
     */
    private fun checkPersonDirected(tokenIndex: Int, rawTokens: List<String>): Boolean {
        for (offset in -PROXIMITY_WINDOW..PROXIMITY_WINDOW) {
            if (offset == 0) continue
            val idx = tokenIndex + offset
            if (idx < 0 || idx >= rawTokens.size) continue
            val normalized = rawTokens[idx].lowercase(Locale.getDefault()).trimEnd { !it.isLetterOrDigit() }
            if (normalized in personDirectedWords) return true
        }
        return false
    }

    private fun classifySeverity(score: Int): String = when {
        score == 1 -> "Mild"
        score <= 3 -> "Moderate"
        else       -> "Severe"
    }
}
