package com.specthinker.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuotasTest {

    @Test
    fun `polishLimit returns plan limit for known plans`() {
        assertEquals(5L, Quotas.polishLimit("free"))
        assertEquals(30L, Quotas.polishLimit("basic"))
        assertEquals(200L, Quotas.polishLimit("pro"))
        assertEquals(1000L, Quotas.polishLimit("lifetime"))
    }

    @Test
    fun `polishLimit returns fallback for unknown plan`() {
        assertEquals(99L, Quotas.polishLimit("unknown", fallback = 99L))
    }

    @Test
    fun `parsePriceToPlan returns empty map for blank input`() {
        assertEquals(emptyMap<String, String>(), Quotas.parsePriceToPlan(null))
        assertEquals(emptyMap<String, String>(), Quotas.parsePriceToPlan(""))
        assertEquals(emptyMap<String, String>(), Quotas.parsePriceToPlan("  "))
    }

    @Test
    fun `parsePriceToPlan parses comma-separated entries`() {
        val m = Quotas.parsePriceToPlan("price_1:basic,price_2:pro,price_3:lifetime")
        assertEquals(3, m.size)
        assertEquals("basic", m["price_1"])
        assertEquals("pro", m["price_2"])
        assertEquals("lifetime", m["price_3"])
    }

    @Test
    fun `parsePriceToPlan ignores malformed entries`() {
        val m = Quotas.parsePriceToPlan("price_1:basic,no_colon_here,price_2:pro,:empty")
        assertEquals(2, m.size)
        assertEquals("basic", m["price_1"])
        assertEquals("pro", m["price_2"])
    }
}
