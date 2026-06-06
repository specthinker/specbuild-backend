package com.specthinker.llm

import com.specthinker.auth.UserRepository
import com.specthinker.spec.Sections
import com.specthinker.testutil.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant

private class StubProvider(override val name: String, private val result: Result<String>) : LlmProvider {
    var calls: Int = 0
        private set

    override suspend fun complete(systemPrompt: String, userPrompt: String): String {
        calls += 1
        return result.getOrElse { throw it }
    }
}

class LlmServiceFallbackTest {

    private fun emptyUserRepo(): UserRepository = mock(UserRepository::class.java)

    @Test
    fun `first provider success returns immediately`() = runTest {
        val p1 = StubProvider("p1", Result.success("ok"))
        val p2 = StubProvider("p2", Result.success("never"))
        val props = baseProps()
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        val svc = LlmService(listOf(p1, p2), quota, props)

        val outcome = svc.polish("t", Sections(goal = "g"), clientId = "alice")

        assertEquals("ok", outcome.content)
        assertEquals("p1", outcome.provider)
        assertEquals(1, p1.calls)
        assertEquals(0, p2.calls)
        assertEquals(1L, quota.snapshot("alice").used)
    }

    @Test
    fun `falls back to next provider on generic failure`() = runTest {
        val p1 = StubProvider("p1", Result.failure(LlmHttpException("p1", 500, "boom")))
        val p2 = StubProvider("p2", Result.success("ok"))
        val props = baseProps()
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        val svc = LlmService(listOf(p1, p2), quota, props)

        val outcome = svc.polish("t", Sections(goal = "g"), clientId = "alice")

        assertEquals("ok", outcome.content)
        assertEquals("p2", outcome.provider)
        assertEquals(1, p1.calls)
        assertEquals(1, p2.calls)
    }

    @Test
    fun `falls back on rate limit`() = runTest {
        val p1 = StubProvider("p1", Result.failure(RateLimitException("p1", 429)))
        val p2 = StubProvider("p2", Result.success("ok"))
        val svc = LlmService(listOf(p1, p2), baseQuota(), baseProps())

        val outcome = svc.polish("t", Sections(), clientId = "bob")

        assertEquals("ok", outcome.content)
        assertEquals("p2", outcome.provider)
    }

    @Test
    fun `falls back on auth error`() = runTest {
        val p1 = StubProvider("p1", Result.failure(AuthException("p1", 401)))
        val p2 = StubProvider("p2", Result.success("ok"))
        val svc = LlmService(listOf(p1, p2), baseQuota(), baseProps())

        val outcome = svc.polish("t", Sections(), clientId = null)

        assertEquals("ok", outcome.content)
        assertEquals("p2", outcome.provider)
    }

    @Test
    fun `all providers failing throws AllProvidersFailedException and refunds quota`() = runTest {
        val p1 = StubProvider("p1", Result.failure(LlmHttpException("p1", 500, "x")))
        val p2 = StubProvider("p2", Result.failure(LlmHttpException("p2", 502, "x")))
        val props = baseProps()
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
        val svc = LlmService(listOf(p1, p2), quota, props)

        val ex = assertThrows(AllProvidersFailedException::class.java) {
            kotlinx.coroutines.runBlocking {
                svc.polish("t", Sections(), clientId = "carol")
            }
        }
        assertNotNull(ex.cause)
        assertEquals(0L, quota.snapshot("carol").used)
    }

    @Test
    fun `quota exceeded blocks the call before any provider is invoked`() = runTest {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val p1 = StubProvider("p1", Result.success("ok"))
        val props = baseProps(quotaLimit = 1)
        val quota = QuotaService(props, emptyUserRepo(), FixedClock(now))
        val svc = LlmService(listOf(p1), quota, props)

        kotlinx.coroutines.runBlocking { svc.polish("t", Sections(), clientId = "dave") }

        val ex = assertThrows(QuotaExceededException::class.java) {
            kotlinx.coroutines.runBlocking { svc.polish("t", Sections(), clientId = "dave") }
        }
        assertEquals(1L, ex.used)
        assertEquals(1L, ex.limit)
        assertTrue(ex.resetsAtEpochMillis > now.toEpochMilli(), "resets-at must be after the test's fixed 'now'")
    }

    private fun baseProps(quotaLimit: Long = 50): LlmProperties = LlmProperties(
        connectTimeout = 1,
        requestTimeout = 1,
        quota = LlmProperties.Quota(enabled = true, perClientPerDay = quotaLimit, defaultClientId = "anonymous"),
        providers = LlmProperties.Providers(
            deepseek = LlmProperties.Providers.Provider(false, "", "", ""),
            openrouterDeepseek = LlmProperties.Providers.Provider(false, "", "", ""),
            openrouterFree = LlmProperties.Providers.Provider(false, "", "", ""),
        ),
    )

    private fun baseQuota(): QuotaService = QuotaService(baseProps(), emptyUserRepo(), FixedClock(Instant.parse("2026-01-15T12:00:00Z")))
}
