package com.example.prototype.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAnalysisEngineTest {

    // ─────────────────────────────────────────────────────────────
    // Stage 1 — Preprocessing
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `leet substitution converts digits and symbols to letters`() {
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("sh1t")
        assertEquals(1, results.size)
        assertEquals("shit", results[0].rawToken)
        assertEquals(0, results[0].levenshteinDistance)
        assertEquals(1.0f, results[0].similarityScore)
    }

    @Test
    fun `trailing exclamation mark is removed not converted`() {
        val engine = TextAnalysisEngine.withWords(setOf("idiot"))
        val results = engine.analyze("idiot!")
        assertEquals(1, results.size)
        assertEquals("idiot", results[0].rawToken)
    }

    @Test
    fun `multiple trailing exclamation marks are all stripped`() {
        val engine = TextAnalysisEngine.withWords(setOf("idiot"))
        val results = engine.analyze("idiot!!")
        assertEquals(1, results.size)
        assertEquals("idiot", results[0].rawToken)
    }

    @Test
    fun `interior exclamation mark is converted to i`() {
        // "b!tch" → interior '!' → 'i' → "bitch"
        val engine = TextAnalysisEngine.withWords(setOf("bitch"))
        val results = engine.analyze("b!tch")
        assertEquals(1, results.size)
        assertEquals("bitch", results[0].rawToken)
    }

    @Test
    fun `at sign converted to a`() {
        // "@ss" → "ass"
        val engine = TextAnalysisEngine.withWords(setOf("ass"))
        val results = engine.analyze("@ss")
        assertEquals(1, results.size)
        assertEquals("ass", results[0].rawToken)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `dollar sign converted to s`() {
        // "$hit" → "shit"
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("\$hit")
        assertEquals(1, results.size)
        assertEquals("shit", results[0].rawToken)
    }

    @Test
    fun `three or more consecutive identical chars collapsed to two`() {
        // "fuuuuuck" → after collapse → "fuuck"
        val engine = TextAnalysisEngine.withWords(setOf("fuck"))
        val results = engine.analyze("fuuuuuck")
        assertEquals(1, results.size)
        assertEquals("fuuck", results[0].rawToken)
        // "fuuck" vs "fuck": dist=1, len=5, sim=0.8 ≥ 0.60 → match
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `short token filter drops tokens of length 2 or less`() {
        // "hi" normalized = "hi" (length 2) → dropped
        val engine = TextAnalysisEngine.withWords(setOf("hi"))
        val results = engine.analyze("hi")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token of length 3 is not dropped by short-token filter`() {
        val engine = TextAnalysisEngine.withWords(setOf("ass"))
        val results = engine.analyze("ass")
        assertEquals(1, results.size)
    }

    // ─────────────────────────────────────────────────────────────
    // Stage 2 — Levenshtein / threshold rules
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `token length 4 or less requires exact match (distance 0)`() {
        // "crap" (len 4) vs "cramp" (dist 1) → must NOT match
        val engine = TextAnalysisEngine.withWords(setOf("cramp"))
        val results = engine.analyze("crap")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token length 4 exact match passes`() {
        val engine = TextAnalysisEngine.withWords(setOf("crap"))
        val results = engine.analyze("crap")
        assertEquals(1, results.size)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `token length 5 with similarity below 0_60 is not flagged`() {
        // "hello" vs "evil" — very different words, sim will be low
        val engine = TextAnalysisEngine.withWords(setOf("evil"))
        val results = engine.analyze("hello")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token length 5 with similarity above 0_60 is flagged`() {
        // "stupi" vs "stupid": dist=1, max=6, sim=0.833 ≥ 0.60
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupi")
        assertEquals(1, results.size)
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `clean text returns empty list`() {
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("hello world this is fine")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `repeated token in input produces one DetectedWord per occurrence`() {
        // "badword badword" → tokenizes to two tokens → two results
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("badword badword")
        assertEquals(2, results.size)
    }

    @Test
    fun `best match per token emits single result even when multiple refs match`() {
        // "hell" matches both "hell" (dist 0) and "help" (dist 1); best = "hell"
        val engine = TextAnalysisEngine.withWords(setOf("hell", "help"))
        val results = engine.analyze("hell")
        assertEquals(1, results.size)
        assertEquals("hell", results[0].matchedWord)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `similarity score is 1_0 for exact match`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupid")
        assertEquals(1, results.size)
        assertEquals(1.0f, results[0].similarityScore)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `combined leet and repeat collapse before matching`() {
        // "5h!1t" → '5'→'s', 'h', '!'→'i' (interior), '1'→'i', 't' → "shiit"
        // after collapse no double-i issues since they're identical → "shiit"
        // vs "shit" (dist=1, len=5, sim=0.8 ≥ 0.60) → match
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("5h!1t")
        assertEquals(1, results.size)
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `punctuation in middle of token is stripped in final cleanup`() {
        // "ba-d.word" → after strip non-alnum → "badword"
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("ba-d.word")
        assertEquals(1, results.size)
        assertEquals("badword", results[0].rawToken)
    }
}
