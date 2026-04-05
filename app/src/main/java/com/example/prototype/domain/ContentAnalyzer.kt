package com.example.prototype.domain

import android.content.Context
import java.util.Locale

object ContentAnalyzer {

    private var wordSet: Set<String> = emptySet()

    fun init(context: Context) {
        val words = mutableSetOf<String>()
        listOf("inappropriate_words_english.txt", "inappropriate_words_tagalog.txt").forEach { filename ->
            context.assets.open(filename).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val word = line.trim().lowercase(Locale.getDefault())
                    if (word.isNotEmpty()) words.add(word)
                }
            }
        }
        wordSet = words
    }

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
            if (wordSet.contains(token)) {
                foundIncidents.add(Incident(token, "HIGH"))
            }
        }

        val distinctIncidents = foundIncidents.distinctBy { it.word }

        return AnalysisResult(
            isClean = distinctIncidents.isEmpty(),
            incidents = distinctIncidents
        )
    }
}