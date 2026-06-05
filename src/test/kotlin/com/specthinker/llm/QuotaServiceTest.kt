package com.specthinker.llm

import com.specthinker.testutil.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class QuotaServiceTest {

    @Test
    fun `disabled quota never blocks`() {
        val props = baseProps(enabled = false, limit = 0)
        val quota = QuotaService(props, FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        repeat(100) { quota.consume("alice") }
        assertEquals(0, quota.snapshot("alice").used)
    }

    @Test
    fun `consume increments and reports remaining`() {
        val props = baseProps(limit = 3)
        val quota = QuotaService(props, FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals(1, quota.consume("alice").used)
        assertEquals(2, quota.consume("alice").used)
        assertEquals(3, quota.consume("alice").used)
    }

    @Test
    fun `consume over limit throws QuotaExceededException`() {
        val props = baseProps(limit = 2)
        val quota = QuotaService(props, FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        quota.consume("alice")
        quota.consume("alice")
        val ex = assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        assertEquals(2, ex.used)
        assertEquals(2, ex.limit)
    }

    @Test
    fun `refund decrements without going below zero`() {
        val props = baseProps(limit = 5)
        val quota = QuotaService(props, FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        quota.consume("alice")
        quota.consume("alice")
        assertEquals(2, quota.snapshot("alice").used)
        quota.refund("alice")
        assertEquals(1, quota.snapshot("alice").used)
        quota.refund("alice")
        quota.refund("alice")
        assertEquals(0, quota.snapshot("alice").used)
    }

    @Test
    fun `different clients have independent counters`() {
        val props = baseProps(limit = 1)
        val quota = QuotaService(props, FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        quota.consume("alice")
        assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        quota.consume("bob")
        assertEquals(1, quota.snapshot("bob").used)
    }

    @Test
    fun `day rollover resets the counter`() {
        val morning = Instant.parse("2026-01-15T08:00:00Z")
        val nextMorning = Instant.parse("2026-01-16T08:00:00Z")
        val quota = QuotaService(baseProps(limit = 1), FixedClock(morning))
        quota.consume("alice")
        assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        val quota2 = QuotaService(baseProps(limit = 1), FixedClock(nextMorning))
        assertEquals(0, quota2.snapshot("alice").used)
    }

    private fun baseProps(enabled: Boolean = true, limit: Long = 50) = LlmProperties(
        quota = LlmProperties.Quota(enabled = enabled, perClientPerDay = limit, defaultClientId = "anonymous"),
    )
}
