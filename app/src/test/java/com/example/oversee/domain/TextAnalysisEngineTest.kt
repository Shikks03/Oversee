package com.example.oversee.domain

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
        val results = engine.analyze("sh1t").detectedWords
        assertEquals(1, results.size)
        assertEquals("shit", results[0].rawToken)
        assertEquals(0, results[0].levenshteinDistance)
        assertEquals(1.0f, results[0].similarityScore)
    }

    @Test
    fun `trailing exclamation mark is removed not converted`() {
        val engine = TextAnalysisEngine.withWords(setOf("idiot"))
        val results = engine.analyze("idiot!").detectedWords
        assertEquals(1, results.size)
        assertEquals("idiot", results[0].rawToken)
    }

    @Test
    fun `multiple trailing exclamation marks are all stripped`() {
        val engine = TextAnalysisEngine.withWords(setOf("idiot"))
        val results = engine.analyze("idiot!!").detectedWords
        assertEquals(1, results.size)
        assertEquals("idiot", results[0].rawToken)
    }

    @Test
    fun `interior exclamation mark is converted to i`() {
        // "b!tch" → interior '!' → 'I' (leet) → "bItch" → lowercase → "bitch"
        val engine = TextAnalysisEngine.withWords(setOf("bitch"))
        val results = engine.analyze("b!tch").detectedWords
        assertEquals(1, results.size)
        assertEquals("bitch", results[0].rawToken)
    }

    @Test
    fun `at sign converted to a`() {
        // "@ss" → "Ass" → lowercase → "ass"
        val engine = TextAnalysisEngine.withWords(setOf("ass"))
        val results = engine.analyze("@ss").detectedWords
        assertEquals(1, results.size)
        assertEquals("ass", results[0].rawToken)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `dollar sign converted to s`() {
        // "$hit" → "Shit" → lowercase → "shit"
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("\$hit").detectedWords
        assertEquals(1, results.size)
        assertEquals("shit", results[0].rawToken)
    }

    @Test
    fun `three or more consecutive identical chars collapsed to two`() {
        // "fuuuuuck" → after collapse → "fuuck"
        val engine = TextAnalysisEngine.withWords(setOf("fuck"))
        val results = engine.analyze("fuuuuuck").detectedWords
        assertEquals(1, results.size)
        assertEquals("fuuck", results[0].rawToken)
        // "fuuck" vs "fuck": dist=1, len=5, sim=0.8 ≥ 0.60 → match
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `short token filter drops tokens of length 2 or less`() {
        // "hi" normalized = "hi" (length 2) → dropped
        val engine = TextAnalysisEngine.withWords(setOf("hi"))
        val results = engine.analyze("hi").detectedWords
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token of length 3 is not dropped by short-token filter`() {
        val engine = TextAnalysisEngine.withWords(setOf("ass"))
        val results = engine.analyze("ass").detectedWords
        assertEquals(1, results.size)
    }

    // ─────────────────────────────────────────────────────────────
    // Stage 2 — Levenshtein / threshold rules
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `token length 4 or less requires exact match (distance 0)`() {
        // "crap" (len 4) vs "cramp" (dist 1) → must NOT match
        val engine = TextAnalysisEngine.withWords(setOf("cramp"))
        val results = engine.analyze("crap").detectedWords
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token length 4 exact match passes`() {
        val engine = TextAnalysisEngine.withWords(setOf("crap"))
        val results = engine.analyze("crap").detectedWords
        assertEquals(1, results.size)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `token length 5 with similarity below 0_60 is not flagged`() {
        // "hello" vs "evil" — very different words, sim will be low
        val engine = TextAnalysisEngine.withWords(setOf("evil"))
        val results = engine.analyze("hello").detectedWords
        assertTrue(results.isEmpty())
    }

    @Test
    fun `token length 5 with distance 1 and repetition signal is flagged`() {
        // "ssshit" → collapses to "sshit" (len 5, hasRepetition=true), dist("sshit","shit")=1
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("ssshit").detectedWords
        assertEquals(1, results.size)
        assertEquals("sshit", results[0].rawToken)
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `token length 5 with distance 1 and leet signal is flagged`() {
        // "stup1" → leet '1'→'I', normalizes to "stupi" (len 5, wasLeetApplied=true), dist=1 vs "stupid"
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stup1").detectedWords
        assertEquals(1, results.size)
        assertEquals("stupi", results[0].rawToken)
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `clean text returns empty list`() {
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("hello world this is fine").detectedWords
        assertTrue(results.isEmpty())
    }

    @Test
    fun `repeated token in input produces one DetectedWord per occurrence`() {
        // "badword badword" → tokenizes to two tokens → two results
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("badword badword").detectedWords
        assertEquals(2, results.size)
    }

    @Test
    fun `best match per token emits single result even when multiple refs match`() {
        // "hell" matches both "hell" (dist 0) and "help" (dist 1); best = "hell"
        val engine = TextAnalysisEngine.withWords(setOf("hell", "help"))
        val results = engine.analyze("hell").detectedWords
        assertEquals(1, results.size)
        assertEquals("hell", results[0].matchedWord)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `similarity score is 1_0 for exact match`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupid").detectedWords
        assertEquals(1, results.size)
        assertEquals(1.0f, results[0].similarityScore)
        assertEquals(0, results[0].levenshteinDistance)
    }

    @Test
    fun `combined leet and repeat collapse before matching`() {
        // "5h!1t" → '5'→'S', 'h', '!'→'I' (interior), '1'→'I', 't' → "ShIIt"
        // after collapse → "ShIIt" (no triple run) → strip → "ShIIt"
        // lowercase → "shiit" vs "shit" (dist=1, len=5, sim=0.8 ≥ 0.60) → match
        val engine = TextAnalysisEngine.withWords(setOf("shit"))
        val results = engine.analyze("5h!1t").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].similarityScore >= 0.60f)
    }

    @Test
    fun `punctuation in middle of token is stripped in final cleanup`() {
        // "ba-d.word" → after strip non-alnum → "badword"
        val engine = TextAnalysisEngine.withWords(setOf("badword"))
        val results = engine.analyze("ba-d.word").detectedWords
        assertEquals(1, results.size)
        assertEquals("badword", results[0].rawToken)
    }

    // ─────────────────────────────────────────────────────────────
    // Boolean flags — isAllCaps, hasPunctuation, hasRepetition
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `isAllCaps true for fully uppercase token`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("STUPID").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].isAllCaps)
    }

    @Test
    fun `isAllCaps false for mixed case token`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("Stupid").detectedWords
        assertEquals(1, results.size)
        assertTrue(!results[0].isAllCaps)
    }

    @Test
    fun `isAllCaps true for leet all-caps token`() {
        // "@SSHOLE" → leet 'A' + "SSHOLE" → "ASSHOLE" → all caps
        val engine = TextAnalysisEngine.withWords(setOf("asshole"))
        val results = engine.analyze("@SSHOLE").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].isAllCaps)
    }

    @Test
    fun `isAllCaps false for leet mixed-case token`() {
        // "@sshole" → leet 'A' + "sshole" → "Asshole" → mixed case
        val engine = TextAnalysisEngine.withWords(setOf("asshole"))
        val results = engine.analyze("@sshole").detectedWords
        assertEquals(1, results.size)
        assertTrue(!results[0].isAllCaps)
    }

    @Test
    fun `hasPunctuation true when token ends with exclamation`() {
        val engine = TextAnalysisEngine.withWords(setOf("idiot"))
        val results = engine.analyze("idiot!!!").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].hasPunctuation)
    }

    @Test
    fun `hasPunctuation true when token ends with question mark`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupid?").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].hasPunctuation)
    }

    @Test
    fun `hasPunctuation false for plain token`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupid").detectedWords
        assertEquals(1, results.size)
        assertTrue(!results[0].hasPunctuation)
    }

    @Test
    fun `hasRepetition true when token contains 3 consecutive identical chars`() {
        val engine = TextAnalysisEngine.withWords(setOf("fuck"))
        val results = engine.analyze("fuuuuck").detectedWords
        assertEquals(1, results.size)
        assertTrue(results[0].hasRepetition)
    }

    @Test
    fun `hasRepetition false for token without repetition`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val results = engine.analyze("stupid").detectedWords
        assertEquals(1, results.size)
        assertTrue(!results[0].hasRepetition)
    }

    // ─────────────────────────────────────────────────────────────
    // tokenIndex and rawTokens
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `tokenIndex reflects position in rawTokens list`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val result = engine.analyze("you are STUPID")
        assertEquals(listOf("you", "are", "STUPID"), result.rawTokens)
        assertEquals(1, result.detectedWords.size)
        assertEquals(2, result.detectedWords[0].tokenIndex)
    }

    @Test
    fun `rawTokens contains all pre-normalization tokens`() {
        val engine = TextAnalysisEngine.withWords(setOf("stupid"))
        val result = engine.analyze("hello world stupid")
        assertEquals(3, result.rawTokens.size)
        assertEquals("hello", result.rawTokens[0])
        assertEquals("world", result.rawTokens[1])
        assertEquals("stupid", result.rawTokens[2])
    }

    // ─────────────────────────────────────────────────────────────
    // FP regression — first-letter anchor and no-signal guard
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ships does not match shits (len 5 dist 1 no signal)`() {
        val engine = TextAnalysisEngine.withWords(setOf("shits"))
        assertTrue(engine.analyze("ships").detectedWords.isEmpty())
    }

    @Test
    fun `ducks does not match fucks (first-letter anchor)`() {
        val engine = TextAnalysisEngine.withWords(setOf("fucks"))
        assertTrue(engine.analyze("ducks").detectedWords.isEmpty())
    }

    @Test
    fun `trucking does not match fucking (first-letter anchor)`() {
        val engine = TextAnalysisEngine.withWords(setOf("fucking"))
        assertTrue(engine.analyze("trucking").detectedWords.isEmpty())
    }

    @Test
    fun `batch does not match bitch (len 5 dist 1 no signal)`() {
        val engine = TextAnalysisEngine.withWords(setOf("bitch"))
        assertTrue(engine.analyze("batch").detectedWords.isEmpty())
    }

    @Test
    fun `tango does not match tanga (len 5 dist 1 no signal)`() {
        val engine = TextAnalysisEngine.withWords(setOf("tanga"))
        assertTrue(engine.analyze("tango").detectedWords.isEmpty())
    }

    @Test
    fun `bigger does not match bugger (len 6 dist 1 no signal)`() {
        val engine = TextAnalysisEngine.withWords(setOf("bugger"))
        assertTrue(engine.analyze("bigger").detectedWords.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // Bigram pass — phrase matching and OCR-split reconstitution
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `split ocr tokens putang ina reassemble to putangina and match single ref`() {
        val engine = TextAnalysisEngine.withWords(setOf("putangina"), emptySet())
        val result = engine.analyze("putang ina")
        assertEquals(1, result.detectedWords.size)
        assertEquals("putangina", result.detectedWords[0].matchedWord)
        assertEquals(0, result.detectedWords[0].tokenIndex)
    }

    @Test
    fun `split ocr tokens match multi-word ref putang ina`() {
        val engine = TextAnalysisEngine.withWords(emptySet(), setOf(listOf("putang", "ina")))
        val result = engine.analyze("putang ina")
        assertEquals(1, result.detectedWords.size)
        assertEquals("putang ina", result.detectedWords[0].matchedWord)
    }

    @Test
    fun `when phrase matches the overlapping single match is suppressed`() {
        val engine = TextAnalysisEngine.withWords(setOf("putang", "putangina"), emptySet())
        val result = engine.analyze("putang ina")
        assertEquals(1, result.detectedWords.size)
        assertEquals("putangina", result.detectedWords[0].matchedWord)
    }

    @Test
    fun `benign bigram does not produce phrase false positive`() {
        val engine = TextAnalysisEngine.withWords(emptySet(), setOf(listOf("putang", "ina")))
        val result = engine.analyze("tango ice")
        assertTrue(result.detectedWords.isEmpty())
    }
}
