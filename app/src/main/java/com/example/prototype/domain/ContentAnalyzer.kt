package com.example.prototype.domain

import java.util.Locale

object ContentAnalyzer {

    // Map Word -> Severity
    private val WORD_DB = mapOf(
        "stupid" to "LOW",
        "idiot" to "LOW",
        "scam" to "MEDIUM",
        "kill" to "HIGH",
        "die" to "HIGH",
        "pvtangina" to "HIGH",
        "bobo" to "MEDIUM",
        "burikat" to "MEDIUM",
        "bangag ka ba?!" to "HIGH",
        "bugak!!" to "HIGH",
        "gago" to "HIGH",
        "8080" to "MEDIUM",
        "@mputa!" to "HIGH",
        "moron" to "MEDIUM",
        "baliw" to "LOW",
        "stp1d!" to "MEDIUM",
        "m0r0n" to "MEDIUM",
        "imb€cil€" to "MEDIUM",
        "stup1d" to "LOW"
    )

    data class Incident(
        val word: String,
        val severity: String
    )

    data class AnalysisResult(
        val isClean: Boolean,
        val incidents: List<Incident>
    )

    fun analyze(rawText: String): AnalysisResult {
        val lowerCaseText = rawText.lowercase(Locale.getDefault())
        // Allow letters, numbers, and spaces
        val scrubbedText = lowerCaseText.replace(Regex("[^a-z0-9 ]"), "")

        val tokens = scrubbedText.split("\\s+".toRegex())
            .filter { it.isNotBlank() && it.length > 2 }

        val foundIncidents = mutableListOf<Incident>()

        for (token in tokens) {
            if (WORD_DB.containsKey(token)) {
                val severity = WORD_DB[token] ?: "LOW"
                foundIncidents.add(Incident(token, severity))
            }
        }

        val distinctIncidents = foundIncidents.distinctBy { it.word }

        return AnalysisResult(
            isClean = distinctIncidents.isEmpty(),
            incidents = distinctIncidents
        )
    }
}