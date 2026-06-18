package com.example.oversee.data.local

import com.example.oversee.data.PairingRepository.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PairingLogicTest {

    @Test
    fun `generateCode produces a six digit zero-padded string`() {
        repeat(100) { seed ->
            val code = PairingLogic.generateCode(Random(seed.toLong()))
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }

    @Test
    fun `generateCode pads small numbers with leading zeros`() {
        val code = PairingLogic.generateCode(object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 42
        })
        assertEquals("000042", code)
    }

    @Test
    fun `isExpired is false just under the ttl`() {
        val created = 1_000_000L
        val now = created + PairingLogic.EXPIRY_MS - 1
        assertFalse(PairingLogic.isExpired(created, now))
    }

    @Test
    fun `isExpired is true exactly at the ttl`() {
        val created = 1_000_000L
        val now = created + PairingLogic.EXPIRY_MS
        assertTrue(PairingLogic.isExpired(created, now))
    }

    @Test
    fun `isExpired is true past the ttl`() {
        val created = 1_000_000L
        val now = created + PairingLogic.EXPIRY_MS + 5_000
        assertTrue(PairingLogic.isExpired(created, now))
    }

    @Test
    fun `intentSummary for ADD does not mention deletion`() {
        val text = PairingLogic.intentSummary(Intent.ADD, null)
        assertTrue(text.contains("new child"))
        assertFalse(text.contains("deleted"))
    }

    @Test
    fun `intentSummary for REPLACE names the target and warns of deletion`() {
        val text = PairingLogic.intentSummary(Intent.REPLACE, "Timmy")
        assertTrue(text.contains("Timmy"))
        assertTrue(text.contains("deleted"))
    }

    @Test
    fun `defaultChildName is one-indexed off the existing count`() {
        assertEquals("Child 1", PairingLogic.defaultChildName(0))
        assertEquals("Child 3", PairingLogic.defaultChildName(2))
    }

    @Test
    fun `intentSummary for ADD ignores any target name`() {
        assertFalse(PairingLogic.intentSummary(Intent.ADD, "Timmy").contains("Timmy"))
    }

    @Test
    fun `intentSummary for REPLACE with null target falls back to this child`() {
        val text = PairingLogic.intentSummary(Intent.REPLACE, null)
        assertTrue(text.contains("this child"))
        assertTrue(text.contains("deleted"))
    }

    @Test
    fun `defaultChildName for one existing child is Child 2`() {
        assertEquals("Child 2", PairingLogic.defaultChildName(1))
    }
}
