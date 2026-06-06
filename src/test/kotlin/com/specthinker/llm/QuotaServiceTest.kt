package com.specthinker.llm

import com.specthinker.auth.UserRepository
import com.specthinker.testutil.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional

class QuotaServiceTest {

    private fun emptyUserRepo(): UserRepository = mock(UserRepository::class.java)

    @Test
    fun `disabled quota never blocks`() {
        val props = baseProps(enabled = false, limit = 0)
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        repeat(100) { quota.consume("alice") }
        assertEquals(0, quota.snapshot("alice").used)
    }

    @Test
    fun `consume increments and reports remaining`() {
        val props = baseProps(limit = 3)
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals(1, quota.consume("alice").used)
        assertEquals(2, quota.consume("alice").used)
        assertEquals(3, quota.consume("alice").used)
    }

    @Test
    fun `consume over limit throws QuotaExceededException`() {
        val props = baseProps(limit = 2)
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        quota.consume("alice")
        quota.consume("alice")
        val ex = assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        assertEquals(2, ex.used)
        assertEquals(2, ex.limit)
    }

    @Test
    fun `refund decrements without going below zero`() {
        val props = baseProps(limit = 5)
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
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
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        quota.consume("alice")
        assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        quota.consume("bob")
        assertEquals(1, quota.snapshot("bob").used)
    }

    @Test
    fun `day rollover resets the counter`() {
        val morning = Instant.parse("2026-01-15T08:00:00Z")
        val nextMorning = Instant.parse("2026-01-16T08:00:00Z")
        val quota = QuotaService(baseProps(limit = 1), emptyUserRepo(), FixedClock(morning))
        quota.consume("alice")
        assertThrows(QuotaExceededException::class.java) { quota.consume("alice") }
        val quota2 = QuotaService(baseProps(limit = 1), emptyUserRepo(), FixedClock(nextMorning))
        assertEquals(0, quota2.snapshot("alice").used)
    }

    @Test
    fun `user quota reflects stored polish_used and resets after a day`() {
        val repo = emptyUserRepo()
        val clock = FixedClock(Instant.parse("2026-01-15T12:00:00Z"))
        val user = user("usr_1", "free", polishUsed = 2L, periodStart = Instant.parse("2026-01-15T00:00:00Z"))
        `when`(repo.findById("usr_1")).thenReturn(Optional.of(user))
        val quota = QuotaService(baseProps(limit = 50), repo, clock)
        val snap = quota.snapshotForUser("usr_1", "free")
        assertEquals(2L, snap.used)
        assertEquals(5L, snap.limit)
    }

    @Test
    fun `consumeForUser increments persisted polish count`() {
        val repo = emptyUserRepo()
        val clock = FixedClock(Instant.parse("2026-01-15T12:00:00Z"))
        val user = user("usr_1", "basic", polishUsed = 0L, periodStart = Instant.parse("2026-01-15T00:00:00Z"))
        `when`(repo.findById("usr_1")).thenReturn(Optional.of(user))
        val quota = QuotaService(baseProps(limit = 50), repo, clock)
        val snap = quota.consumeForUser("usr_1", "basic")
        assertEquals(1L, snap.used)
        assertEquals(30L, snap.limit)
    }

    @Test
    fun `consumeForUser resets when period_start is from a previous day`() {
        val repo = emptyUserRepo()
        val clock = FixedClock(Instant.parse("2026-01-15T12:00:00Z"))
        val user = user("usr_1", "basic", polishUsed = 29L, periodStart = Instant.parse("2026-01-14T08:00:00Z"))
        `when`(repo.findById("usr_1")).thenReturn(Optional.of(user))
        val quota = QuotaService(baseProps(limit = 50), repo, clock)
        val snap = quota.consumeForUser("usr_1", "basic")
        assertEquals(1L, snap.used)
        assertEquals(30L, snap.limit)
    }

    @Test
    fun `consumeForUser throws when over plan limit`() {
        val repo = emptyUserRepo()
        val clock = FixedClock(Instant.parse("2026-01-15T12:00:00Z"))
        val user = user("usr_1", "free", polishUsed = 5L, periodStart = Instant.parse("2026-01-15T00:00:00Z"))
        `when`(repo.findById("usr_1")).thenReturn(Optional.of(user))
        val quota = QuotaService(baseProps(limit = 50), repo, clock)
        val ex = assertThrows(QuotaExceededException::class.java) {
            quota.consumeForUser("usr_1", "free")
        }
        assertEquals(5L, ex.used)
        assertEquals(5L, ex.limit)
    }

    private fun baseProps(enabled: Boolean = true, limit: Long = 50) = LlmProperties(
        quota = LlmProperties.Quota(enabled = enabled, perClientPerDay = limit, defaultClientId = "anonymous"),
    )

    private fun user(id: String, plan: String, polishUsed: Long, periodStart: Instant) =
        com.specthinker.auth.User(
            id = id,
            email = "u@example.com",
            plan = plan,
            planSetAt = periodStart,
            periodStart = periodStart,
            specsUsed = 0L,
            polishUsed = polishUsed,
            stripeCustomerId = null,
            createdAt = periodStart,
        )
}
